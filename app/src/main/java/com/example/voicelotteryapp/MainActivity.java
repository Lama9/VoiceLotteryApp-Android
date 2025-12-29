package com.example.voicelotteryapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.example.voicelotteryapp.manager.PrizeManager;
import com.example.voicelotteryapp.model.Prize;
import com.example.voicelotteryapp.view.WheelView;

import java.util.List;
import java.util.Random;

/**
 * 主界面 Activity
 * 负责应用的核心交互逻辑，包括：
 * 1. 转盘的显示与控制
 * 2. 语音控制功能的集成
 * 3. 局域网远程控制服务的启停
 * 4. 抽奖逻辑的调度（开始、停止、显示结果）
 */
public class MainActivity extends AppCompatActivity {

    // UI 组件引用
    private WheelView mWheelView;       // 自定义转盘视图
    private Button mBtnAction;          // 核心操作按钮（开始/停止）
    private ImageButton mBtnSettings;   // 设置按钮
    private ImageButton mBtnMic;        // 语音控制开关按钮
    
    // 状态标志位
    private boolean isStarted = false;     // 标记转盘是否正在旋转
    private boolean isVoiceActive = false; // 标记语音控制是否已开启

    // 权限请求码常量
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置当前 Activity 的布局文件
        setContentView(R.layout.activity_main);

        // 初始化视图组件，绑定 XML 中的控件
        mWheelView = findViewById(R.id.wheelView);
        mBtnAction = findViewById(R.id.btnAction);
        mBtnSettings = findViewById(R.id.btnSettings);
        mBtnMic = findViewById(R.id.btnMic);
        
        // 初始化监听器（点击事件等）
        initListener();
        // 初始化语音控制组件
        initVoiceControl();
    }

    /**
     * Activity 生命周期：onResume
     * 当 Activity 每次回到前台（对用户可见）时调用。
     * 用于刷新数据和恢复服务状态。
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回主界面时重新加载数据（防止在设置页修改了奖品后主界面不更新）
        loadData();
        // 更新调试信息的显示状态
        updateDebugInfo(); 
        // 启动或检查远程控制服务
        initServer();
        // 恢复背景声音（如果应用回到前台，允许播放）
        com.example.voicelotteryapp.manager.SoundManager.getInstance(this).onResume();
    }
    
    /**
     * Activity 生命周期：onPause
     * 当 Activity 暂停（比如切到后台或锁屏）时调用。
     */
    @Override
    protected void onPause() {
        super.onPause();
        // 暂停背景声音，避免在后台播放打扰用户
        com.example.voicelotteryapp.manager.SoundManager.getInstance(this).onPause();
    }

    /**
     * 加载奖品数据并设置给转盘视图
     */
    private void loadData() {
        // 使用单例 PrizeManager 获取当前需要显示的奖品列表
        // 注意：getDisplayPrizes() 会自动处理总概率不足 100% 时的自动填充（如“谢谢参与”）
        List<Prize> prizes = PrizeManager.getInstance(this).getDisplayPrizes();
        mWheelView.setPrizes(prizes);
    }
    
    /**
     * 根据设置中的开关，显示或隐藏调试信息面板
     */
    private void updateDebugInfo() {
        // 如果调试模式未开启，直接隐藏整个调试布局
        if (!PrizeManager.getInstance(this).isDebugMode()) {
            findViewById(R.id.layoutDebug).setVisibility(View.GONE);
            return;
        }

        // 否则显示布局，并刷新库存数据
        findViewById(R.id.layoutDebug).setVisibility(View.VISIBLE);
        refreshStockView();
        
        // 如果是首次显示，添加一个日志起始标记
        android.widget.TextView tvLog = findViewById(R.id.tvDebugLog);
        if (tvLog.getText().toString().equals("Wait for draw...")) {
             tvLog.setText("=== LOG STARTED ===\n");
        }
    }
    
    /**
     * 刷新调试面板中的库存信息
     */
    private void refreshStockView() {
        if (!PrizeManager.getInstance(this).isDebugMode()) return;
        
        android.widget.TextView tvStock = findViewById(R.id.tvDebugStock);
        StringBuilder sb = new StringBuilder();
        sb.append("[库存状态]\n");
        
        List<Prize> list = PrizeManager.getInstance(this).getConfiguredPrizes();
        for (Prize p : list) {
            sb.append(p.getName()).append(": ");
            if (p.isUnlimited()) {
                sb.append("∞ (无限)\n");
            } else {
                sb.append(p.getRemainingCount()).append("/").append(p.getTotalCount()).append("\n");
            }
        }
        tvStock.setText(sb.toString());
    }

    /**
     * 在调试日志中追加抽奖结果
     * @param targetName 原始随机命中的奖品（可能无库存）
     * @param actualName 实际发放的奖品（经过库存检查后）
     */
    private void appendDebugResult(String targetName, String actualName) {
        if (!PrizeManager.getInstance(this).isDebugMode()) return;
        
        // 先刷新库存显示
        refreshStockView();
        
        android.widget.TextView tvLog = findViewById(R.id.tvDebugLog);
        String current = tvLog.getText().toString();
        // 构造日志内容
        String resultLog = "\n[结果]\n目标: " + targetName + "\n实际: " + actualName + "\n";
        tvLog.setText(current + resultLog);
        
        // 自动滚动到底部
        android.widget.ScrollView sv = (android.widget.ScrollView) tvLog.getParent();
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    // --- 服务器与网络模块 ---
    private com.example.voicelotteryapp.server.LocalServer mServer;
    private AlertDialog mResultDialog; // 保存当前显示的对话框引用，方便自动关闭

    // --- 核心抽奖逻辑 ---
    
    /**
     * 开始抽奖流程
     */
    private void startLottery() {
        // 如果已有结果弹窗在显示，先关闭它
        if (mResultDialog != null && mResultDialog.isShowing()) {
            mResultDialog.dismiss();
        }

        if (!isStarted) {
            // 命令转盘视图开始旋转动画
            mWheelView.start();
            
            // 更新按钮状态为“停止”
            mBtnAction.setText("停止抽奖"); 
            mBtnAction.setBackgroundResource(R.drawable.bg_button_cloud_stop); // 切换为橙色警告样式
            isStarted = true;
            
            // 播放音效
            // 1. 设置状态为旋转中（这将暂停暖场闲置音乐）
            com.example.voicelotteryapp.manager.SoundManager.getInstance(this).setSpinningState(true);
            // 2. 播放启动音效
            com.example.voicelotteryapp.manager.SoundManager.getInstance(this).playStartSound();
        }
    }
    
    /**
     * 停止抽奖并计算结果
     * 注意：这并不意味着转盘立即停止，而是触发减速动画
     * @return 计算出的最终奖品对象
     */
    private Prize stopLotteryAndGetResult() {
        if (isStarted) {
            // 暂时禁用按钮，防止重复点击
            mBtnAction.setEnabled(false);
            mBtnAction.setText("抽奖中...");

            // 核心逻辑：调用 Manager 获取基于权重的随机结果（含库存检查）
            PrizeManager.DrawResult result = PrizeManager.getInstance(MainActivity.this).drawPrize();
            Prize targetPrize = result.actual;

            // 根据奖品名称找到它在转盘列表中的索引位置，以便让转盘停在正确的角度
            List<Prize> displayedPrizes = mWheelView.getPrizes();
            int targetIndex = 0;
            boolean found = false;
            for (int i = 0; i < displayedPrizes.size(); i++) {
                if (displayedPrizes.get(i).getName().equals(targetPrize.getName())) {
                    targetIndex = i;
                    found = true;
                    break;
                }
            }
            if (!found) targetIndex = 0;

            // 记录调试日志
            String targetName = result.original.getName();
            String actualName = result.actual.getName();
            appendDebugResult(targetName, actualName);

            // 指示转盘停在指定索引位置
            mWheelView.stop(targetIndex);
            
            // 注意：结果音效会在 WheelView 动画结束的回调中播放
            return targetPrize;
        }
        return null;
    }

    /**
     * 初始化本地 HTTP 服务器 (NanoHTTPD)
     * 用于通过局域网接收远程控制命令
     */
    private void initServer() {
        // 如果服务器已经在运行，不仅行重复启动
        if (mServer != null && mServer.isAlive()) {
             android.util.Log.d("LocalServer", "Server already running");
            return;
        }
        
        // 停止可能存在的僵尸实例
        stopServer();
        
        // 从配置中读取端口号
        int port = PrizeManager.getInstance(this).getServerPort();

        try {
            // 创建服务器实例，并定义回调接口
            mServer = new com.example.voicelotteryapp.server.LocalServer(port, new com.example.voicelotteryapp.server.LocalServer.ServerCallback() {
                 
                 // 处理 /api/status 请求
                 @Override
                 public org.json.JSONObject onGetStatus() {
                    org.json.JSONObject json = new org.json.JSONObject();
                    try {
                        json.put("status", isStarted ? "running" : "idle"); // 当前运行状态
                        json.put("device_ip", getLocalIpAddress());         // 当前设备IP
                        json.put("port", port);
                    } catch (org.json.JSONException e) { e.printStackTrace(); }
                    return json;
                }
                
                // 处理 /api/start 请求 (远程开始)
                @Override
                public org.json.JSONObject onRemoteStart() {
                    org.json.JSONObject json = new org.json.JSONObject();
                    try {
                        if (isStarted) {
                            json.put("success", false);
                            json.put("message", "Already running");
                        } else {
                            // UI 操作必须切换回主线程
                            runOnUiThread(() -> startLottery());
                            json.put("success", true);
                            json.put("message", "Started");
                        }
                    } catch (org.json.JSONException e) { e.printStackTrace(); }
                    return json;
                }

                // 处理 /api/stop 请求 (远程停止)
                @Override
                public org.json.JSONObject onRemoteStop() {
                    org.json.JSONObject json = new org.json.JSONObject();
                    try {
                        if (!isStarted) {
                            json.put("success", false);
                            json.put("message", "Not running");
                        } else {
                            // 由于停止逻辑会产生结果，我们需要等待 UI 线程计算完成
                            final Prize[] resultHolder = new Prize[1];
                            // 使用 CountDownLatch 进行线程同步等待
                            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                            
                            runOnUiThread(() -> {
                                resultHolder[0] = stopLotteryAndGetResult();
                                latch.countDown(); // 释放锁
                            });
                            
                            try {
                                // 最多等待 2 秒
                                latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException e) { e.printStackTrace(); }
                            
                            // 返回结果给远程客户端
                            if (resultHolder[0] != null) {
                                json.put("success", true);
                                json.put("prize_name", resultHolder[0].getName());
                                json.put("prize_color", resultHolder[0].getColor());
                            } else {
                                json.put("success", false);
                                json.put("message", "Failed to stop or timeout");
                            }
                        }
                    } catch (org.json.JSONException e) { e.printStackTrace(); }
                    return json;
                }
            });
            // 启动服务器
            mServer.start(5000); // 启动超时时间 5秒
            android.util.Log.d("LocalServer", "Server started successfully on port " + port);
            android.widget.Toast.makeText(this, "服务启动成功 端口:" + port, android.widget.Toast.LENGTH_SHORT).show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            android.util.Log.e("LocalServer", "Failed to start server: " + e.getMessage());
            android.widget.Toast.makeText(this, "服务启动失败! 端口被占用?", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    // 停止服务器
    private void stopServer() {
        if (mServer != null) {
            mServer.stop();
            mServer = null;
        }
    }
    
    // Activity 销毁时清理资源
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
        // 停止语音识别服务
        com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).stopListening();
        // 释放音频资源
        com.example.voicelotteryapp.manager.SoundManager.getInstance(this).onDestroy();
    }

    /**
     * 获取本机 IPv4 地址
     */
    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface intf = en.nextElement();
                java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    // 仅返回非回环的 IPv4 地址
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException ex) {
            ex.printStackTrace();
        }
        return "Unknown";
    }

    /**
     * 初始化 UI 交互监听器
     */
    private void initListener() {
        // 大按钮点击逻辑：根据状态开始或停止
        mBtnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isStarted) {
                    startLottery();
                } else {
                    stopLotteryAndGetResult();
                }
            }
        });
        
        // 设置按钮：跳转到设置页
        mBtnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // 转盘旋转结束回调（动画真的停下来了）
        mWheelView.setOnFinishListener(new WheelView.OnFinishListener() {
            @Override
            public void onFinish(Prize outcome) {
                // 重置状态
                isStarted = false;
                mBtnAction.setText("开始抽奖"); 
                mBtnAction.setBackgroundResource(R.drawable.bg_button_cloud);
                mBtnAction.setEnabled(true);
                
                // 音效：播放对应奖项的结果音（基于其在列表中的位置）
                List<Prize> displayed = mWheelView.getPrizes();
                int index = displayed.indexOf(outcome);
                com.example.voicelotteryapp.manager.SoundManager.getInstance(MainActivity.this).playResultSound(index);
                
                // 恢复闲置状态（允许播放暖场音乐）
                com.example.voicelotteryapp.manager.SoundManager.getInstance(MainActivity.this).setSpinningState(false);

                // 显示中奖弹窗
                showCustomDialog(outcome);
            }
        });
    }


    // --- 语音控制模块 (Vosk) ---
    private void initVoiceControl() {
        // 设置回调以接收语音命令
        com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).setListener(new com.example.voicelotteryapp.manager.VoiceManager.VoiceListener() {
            @Override
            public void onCommand(String command) {
                // 根据识别到的关键词执行操作
                switch (command) {
                    case "start": // “开始”、“启动”
                        if (mResultDialog != null && mResultDialog.isShowing()) {
                            mResultDialog.dismiss();
                        }
                        if (!isStarted && mBtnAction.isEnabled()) {
                            startLottery();
                            android.widget.Toast.makeText(MainActivity.this, "语音指令: 开始", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case "stop": // “停止”、“停”
                        if (isStarted && mBtnAction.isEnabled()) {
                            stopLotteryAndGetResult();
                            android.widget.Toast.makeText(MainActivity.this, "语音指令: 停止", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case "exit": // “退出程序”
                        finishAffinity();
                        System.exit(0);
                        break;
                    case "enable_log": // “打开日志”
                        PrizeManager.getInstance(MainActivity.this).setDebugMode(true);
                        updateDebugInfo();
                        android.widget.Toast.makeText(MainActivity.this, "已开启日志", android.widget.Toast.LENGTH_SHORT).show();
                        break;
                    case "disable_log": // “关闭日志”
                        PrizeManager.getInstance(MainActivity.this).setDebugMode(false);
                        updateDebugInfo();
                        android.widget.Toast.makeText(MainActivity.this, "已关闭日志", android.widget.Toast.LENGTH_SHORT).show();
                        break;
                }
            }

            @Override
            public void onError(String message) {
                android.widget.Toast.makeText(MainActivity.this, "语音错误: " + message, android.widget.Toast.LENGTH_SHORT).show();
                 setMicState(false);
            }

            @Override
            public void onReady() {
                // 模型加载完毕
            }

            @Override
            public void onInitFailed(String error) {
                 android.widget.Toast.makeText(MainActivity.this, "模型加载失败: " + error, android.widget.Toast.LENGTH_LONG).show();
                 setMicState(false);
            }

            @Override
            public void onLog(String message) {
                // 仅在调试模式下显示实时语音识别日志
                if (!PrizeManager.getInstance(MainActivity.this).isDebugMode()) {
                    return; 
                }
                
                android.widget.TextView tvLog = findViewById(R.id.tvDebugLog);
                View layoutDebug = findViewById(R.id.layoutDebug);
                if (layoutDebug != null && layoutDebug.getVisibility() != View.VISIBLE) {
                     layoutDebug.setVisibility(View.VISIBLE);
                }

                if (tvLog != null) {
                    String current = tvLog.getText().toString();
                    if (current.length() > 2000) current = current.substring(0, 1000); // 截断过长日志
                    String newEntry = "\n[Voice] " + message;
                    tvLog.setText(current + newEntry);
                    // 滚动到底部
                    final android.widget.ScrollView sv = (android.widget.ScrollView) tvLog.getParent();
                    sv.post(() -> sv.fullScroll(android.view.View.FOCUS_DOWN));
                }
            }
        });

        // 初始化语音管理器（含模型加载）
        com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).init();
        
        // 绑定麦克风按钮点击事件
        mBtnMic.setOnClickListener(v -> toggleVoice());
        
        // 初始化音频管理器
        com.example.voicelotteryapp.manager.SoundManager.getInstance(this).init();
        
        // 关键逻辑：防止自我触发 (智能互斥)
        // 当 SoundManager 播放系统音效时，暂停语音识别；音效结束后恢复。
        com.example.voicelotteryapp.manager.SoundManager.getInstance(this).setStatusListener(new com.example.voicelotteryapp.manager.SoundManager.OnPlayStatusListener() {
            @Override
            public void onPlayStart() {
                // 暂停语音识别
                com.example.voicelotteryapp.manager.VoiceManager.getInstance(MainActivity.this).setPaused(true);
            }

            @Override
            public void onPlayEnd() {
                // 恢复语音识别
                com.example.voicelotteryapp.manager.VoiceManager.getInstance(MainActivity.this).setPaused(false);
            }
        });
    }

    /**
     * 切换语音控制功能的开关状态
     */
    private void toggleVoice() {
        if (isVoiceActive) {
            // 如果已经是开启状态，则关闭
            com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).stopListening();
            setMicState(false);
        } else {
            // 如果是关闭状态，先检查权限
            int permission = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO);
            if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 申请权限
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_RECORD_AUDIO);
            } else {
                // 开始监听
                com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).startListening();
                setMicState(true);
            }
        }
    }
    
    /**
     * 更新麦克风按钮的视觉状态
     */
    private void setMicState(boolean active) {
        isVoiceActive = active;
        if (active) {
            mBtnMic.setImageResource(R.drawable.ic_mic); 
            mBtnMic.setColorFilter(Color.parseColor("#2E7D32")); // 绿色表示开启
        } else {
            mBtnMic.setImageResource(R.drawable.ic_mic_off);
            mBtnMic.setColorFilter(Color.parseColor("#5D4037")); // 褐色表示关闭
        }
    }

    /**
     * 权限申请结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // 用户同意了权限，自动开启
                com.example.voicelotteryapp.manager.VoiceManager.getInstance(this).startListening();
                setMicState(true);
            } else {
                android.widget.Toast.makeText(this, "需要录音权限才能使用语音控制", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 显示自定义的中奖结果弹窗
     */
    private void showCustomDialog(Prize outcome) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // 加载自定义布局
        View view = getLayoutInflater().inflate(R.layout.dialog_result, null);
        
        android.widget.TextView tvMessage = view.findViewById(R.id.tvDialogMessage);
        
        tvMessage.setText("你抽到了： " + outcome.getName() + " ✨");
        
        builder.setView(view);
        AlertDialog dialog = builder.create();
        mResultDialog = dialog; // 记录引用用于后续关闭
        
        // 设置背景透明，以便显示圆角效果
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }

        view.findViewById(R.id.btnRestart).setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
}
