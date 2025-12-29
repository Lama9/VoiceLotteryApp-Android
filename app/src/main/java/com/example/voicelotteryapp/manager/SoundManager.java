package com.example.voicelotteryapp.manager;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.Random;

/**
 * 音频播放管理器 (单例模式)
 * 负责应用内所有的声音播放，包括：
 * 1. 背景暖场音乐 (Idle Loop)
 * 2. 抽奖音效 (启动、停止、中奖结果)
 * 3. 播放状态管理 (与语音识别互斥)
 */
public class SoundManager {
    private static final String TAG = "SoundManager";
    private static SoundManager instance;
    private Context mContext;
    private MediaPlayer mMediaPlayer; // 媒体播放器实例
    private Handler mHandler = new Handler(Looper.getMainLooper()); // 用于在主线程延时执行任务
    private Random mRandom = new Random();
    
    // 状态控制标志
    private boolean isIdleLoopEnabled = false; // 是否开启闲置循环
    private boolean isSpinning = false;        // 是否正在抽奖中 (优先级高)
    
    /**
     * 定义一个 Runnable 任务，实现周期性播放暖场语音
     * 逻辑：播放 -> 等待随机时间(20-30s) -> 再次播放
     */
    private Runnable mIdleTask = new Runnable() {
        @Override
        public void run() {
            // 如果功能关闭或正在抽奖，不执行
            if (!isIdleLoopEnabled || isSpinning) return;
            
            playRandomIdleSound();
            
            // 调度下一次播放，间隔 20-30 秒随机
            long nextInterval = 20000 + mRandom.nextInt(10000);
            mHandler.postDelayed(this, nextInterval);
        }
    };

    private SoundManager(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context);
        }
        return instance;
    }

    /**
     * 初始化
     * 启动闲置循环机制
     */
    public void init() {
        enableIdleLoop(true);
    }
    
    /**
     * 开启/关闭闲置暖场循环
     */
    public void enableIdleLoop(boolean enable) {
        this.isIdleLoopEnabled = enable;
        mHandler.removeCallbacks(mIdleTask); // 先移除旧的任务，防止重复
        if (enable) {
            // 延时 5 秒后开始首次播放，避免刚进应用就吵闹
            mHandler.postDelayed(mIdleTask, 5000); 
        } else {
            stopPlayback();
        }
    }
    
    /**
     * 设置抽奖状态
     * @param spinning true=正在抽奖(高优先级，停止闲置音乐); false=抽奖结束(恢复闲置音乐)
     */
    public void setSpinningState(boolean spinning) {
        this.isSpinning = spinning;
        if (spinning) {
            // 立即停止当前的闲置音乐（如果有）
            stopPlayback();
            mHandler.removeCallbacks(mIdleTask);
        } else {
            // 抽奖结束，稍后恢复闲置循环
            enableIdleLoop(true);
        }
    }

    /**
     * 随机播放一个闲置语音 (暖场语)
     */
    private void playRandomIdleSound() {
        // 定义资源名称列表，需要在 res/raw 中有对应的文件
        String[] idleFiles = {"idle_welcome_1", "idle_welcome_2"};
        String target = idleFiles[mRandom.nextInt(idleFiles.length)];
        int resId = getRawResId(target);
        if (resId != 0) {
            playSound(resId);
        }
    }

    /**
     * 播放转盘启动音效
     */
    public void playStartSound() {
        int resId = getRawResId("sfx_spin_start");
        if (resId != 0) {
            playSound(resId);
        }
    }
    
    /**
     * 播放结果播报语音
     * 规则：根据奖品在列表中的索引查找文件，如 prize_rank_0.m4a
     */
    public void playResultSound(int prizeIndex) {
        String name = "prize_rank_" + prizeIndex;
        int resId = getRawResId(name);
        
        if (resId != 0) {
            playSound(resId);
        } else {
            Log.w(TAG, "未找到对应的结果音频文件: " + name);
        }
    }

    // --- 播放状态监听接口 ---
    private OnPlayStatusListener mStatusListener;
    
    public interface OnPlayStatusListener {
        void onPlayStart(); // 开始播放
        void onPlayEnd();   // 播放结束
    }
    
    public void setStatusListener(OnPlayStatusListener listener) {
        this.mStatusListener = listener;
    }
    
    /**
     * 核心播放方法
     * @param resId 原始资源ID (R.raw.xxx)
     */
    private void playSound(int resId) {
        if (!isSoundEnabled()) return; // 检查全局开关，如果关闭则静音

        stopPlayback(); // 播放新声音前，先停止旧的
        
        try {
            mMediaPlayer = new MediaPlayer();
            AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resId);
            if (afd == null) return;
            
            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            
            // 通知监听器：播放开始
            if (mStatusListener != null) mStatusListener.onPlayStart();
            
            // 监听播放完成
            mMediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mMediaPlayer = null;
                // 通知监听器：播放结束
                if (mStatusListener != null) mStatusListener.onPlayEnd();
            });
            
            // 监听错误
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                mMediaPlayer = null;
                // 出错了也视为结束，以免阻塞后续逻辑
                if (mStatusListener != null) mStatusListener.onPlayEnd();
                return true;
            });
            
        } catch (IOException e) {
            e.printStackTrace();
            // 异常也视为结束
            if (mStatusListener != null) mStatusListener.onPlayEnd();
        }
    }

    /**
     * 辅助方法：检查设置中是否开启了声音
     */
    private boolean isSoundEnabled() {
        return com.example.voicelotteryapp.manager.PrizeManager.getInstance(mContext).isSoundEnabled();
    }

    /**
     * 停止并释放 MediaPlayer
     */
    private void stopPlayback() {
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.release();
            } catch (Exception e) { e.printStackTrace(); }
            mMediaPlayer = null;
        }
    }
    
    /**
     * 根据文件名字符串获取资源 ID (反射机制)
     */
    private int getRawResId(String name) {
        return mContext.getResources().getIdentifier(name, "raw", mContext.getPackageName());
    }

    /**
     * 生命周期处理：暂停
     */
    public void onPause() {
        stopPlayback();
        mHandler.removeCallbacks(mIdleTask); // 停止闲置循环
    }
    
    /**
     * 生命周期处理：恢复
     */
    public void onResume() {
        if (!isSpinning) {
            enableIdleLoop(true); // 恢复闲置循环
        }
    }
    
    /**
     * 销毁清理
     */
    public void onDestroy() {
        stopPlayback();
        mHandler.removeCallbacksAndMessages(null); // 清除所有消息
        instance = null;
    }
}
