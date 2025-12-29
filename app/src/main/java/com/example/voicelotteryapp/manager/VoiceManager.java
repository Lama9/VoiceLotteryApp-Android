package com.example.voicelotteryapp.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;

/**
 * 语音控制管理器 (单例模式)
 * 基于 Vosk 离线语音识别库实现。
 * 负责：
 * 1. 语音模型的加载与初始化
 * 2. 录音与实时识别
 * 3. 关键词匹配 ("开始", "停止" 等)
 * 4. 识别状态控制 (暂停/恢复，防止音效干扰)
 */
public class VoiceManager {

    private static final String TAG = "VoiceManager";
    private static VoiceManager instance;
    
    // Vosk 核心组件
    private SpeechService speechService;
    private Model model;
    
    private VoiceListener listener;
    private Context context;
    private boolean isListening = false;
    private boolean isInitializing = false;
    private boolean isPaused = false;

    // 主线程 Handler，用于将回调抛回主线程
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 语音事件监听接口
     */
    public interface VoiceListener {
        void onCommand(String command);      // 识别到有效指令
        void onError(String message);        // 发生错误
        void onReady();                      // 模型加载完毕
        void onInitFailed(String error);     // 初始化失败
        void onLog(String message);          // 实时识别日志（用于调试）
    }

    private VoiceManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized VoiceManager getInstance(Context context) {
        if (instance == null) {
            instance = new VoiceManager(context);
        }
        return instance;
    }

    public void setListener(VoiceListener listener) {
        this.listener = listener;
    }

    /**
     * 初始化语音识别模块
     * 主要是从 assets 目录解压模型文件到设备内部存储，并加载到内存中。
     * 这是一个耗时操作，在子线程中进行。
     */
    public void init() {
        if (model != null) {
            if (listener != null) listener.onReady();
            return;
        }

        if (isInitializing) return;
        isInitializing = true;

        new Thread(() -> {
            try {
                // 使用 StorageService 解压 assets 中的 "model-cn" 目录
                // "model" 是解压后的目标目录名
                StorageService.unpack(context, "model-cn", "model",
                        (model) -> {
                            this.model = model;
                            isInitializing = false;
                            Log.d(TAG, "Vosk Model 加载成功");
                            mainHandler.post(() -> {
                                if (listener != null) listener.onReady();
                            });
                        },
                        (exception) -> {
                            isInitializing = false;
                            Log.e(TAG, "模型加载失败: " + exception.getMessage());
                            mainHandler.post(() -> {
                                if (listener != null) listener.onInitFailed("Model unpack failed: " + exception.getMessage());
                            });
                        });
            } catch (Exception e) {
                isInitializing = false;
                Log.e(TAG, "初始化错误: " + e.getMessage());
                mainHandler.post(() -> {
                    if (listener != null) listener.onInitFailed(e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 开始监听麦克风
     */
    public void startListening() {
        if (model == null) {
            if (listener != null) listener.onError("模型尚未加载完成");
            return;
        }
        if (isListening) return;

        try {
            // 配置识别器
            // 16000.0f 是采样率
            // grammar 是关键词列表 (JSON数组格式字符串)。
            // 只有列表中的词会被识别，可以显著提高准确率和响应速度。
            // 我们加入了一些语气词（请、吧、了）以支持自然语言，如“请开始吧”。
            String grammar = "[\"[unk]\", \"开始\", \"抽奖\", \"启动\", \"停\", \"停止\", \"结束\", \"退出\", \"程序\", \"关闭\", \"听\", \"请\", \"了\", \"吧\", \"啊\", \"旋转\", \"转盘\", \"日志\", \"打开\", \"显示\", \"隐藏\"]";
            Recognizer recognizer = new Recognizer(model, 16000.0f, grammar);
            
            speechService = new SpeechService(recognizer, 16000.0f);
            
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    // 部分识别结果（说话过程中实时返回）
                    // 开启部分结果检查可以实现更快的响应（例如刚说完“开始”就触发，不用等停顿）
                   checkKeywords(hypothesis, true);
                }

                @Override
                public void onResult(String hypothesis) {
                    // 最终识别结果（一段话结束）
                    checkKeywords(hypothesis, false);
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    checkKeywords(hypothesis, false);
                }

                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "识别错误: " + exception.getMessage());
                    if (listener != null) listener.onError(exception.getMessage());
                    isListening = false;
                }

                @Override
                public void onTimeout() {
                    Log.d(TAG, "识别超时");
                    isListening = false;
                }
            });
            
            isListening = true;
            Log.d(TAG, "开始监听...");
            
        } catch (IOException e) {
            e.printStackTrace();
            if (listener != null) listener.onError("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止监听
     */
    public void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        isListening = false;
        Log.d(TAG, "停止监听");
    }
    
    /**
     * 暂停/恢复识别
     * 主要用于在播放音效时暂停识别，防止音效被误识别为指令。
     */
    public void setPaused(boolean paused) {
        if (!isListening) return;
        
        this.isPaused = paused;
        Log.d(TAG, "语音识别暂停状态: " + paused);
        
        if (speechService != null) {
            speechService.setPause(paused);
        }
    }

    public void onDestroy() {
        stopListening();
        if (model != null) {
            model.close();
            model = null;
        }
    }

    /**
     * 解析识别结果并匹配关键词
     */
    private void checkKeywords(String jsonResult, boolean isPartial) {
        try {
            JSONObject json = new JSONObject(jsonResult);
            String text = "";
            // Vosk 返回的 JSON 格式不同：
            // onPartialResult 返回 {"partial": "..."}
            // onResult 返回 {"text": "..."}
            if (isPartial) {
                text = json.optString("partial", "");
            } else {
                text = json.optString("text", "");
            }

            if (text == null || text.isEmpty()) return;

            Log.d(TAG, "识别内容: " + text);
            final String logText = text;
            mainHandler.post(() -> {
                if (listener != null) listener.onLog(isPartial ? "[部分] " + logText : "[最终] " + logText);
            });

            // 简单的中文关键词匹配逻辑
            if (text.contains("开始") || text.contains("抽奖") || text.contains("启动")) {
                notifyCommand("start");
                resetRecognizer(); // 重置识别器状态，防止重复触发
            } else if (text.contains("停") || text.contains("结束")  || text.contains("停止") || text.contains("听")) {
                notifyCommand("stop");
                resetRecognizer();
            } else if (text.contains("退出") || (text.contains("关闭") && text.contains("程序"))) {
                notifyCommand("exit");
                resetRecognizer();
            } else if (text.contains("日志") && (text.contains("打开") || text.contains("显示"))) {
                notifyCommand("enable_log");
                resetRecognizer();
            } else if (text.contains("日志") && (text.contains("关闭") || text.contains("隐藏"))) {
                notifyCommand("disable_log");
                resetRecognizer();
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    // 通知监听者执行命令
    private void notifyCommand(String cmd) {
        mainHandler.post(() -> {
            if (listener != null) listener.onCommand(cmd);
        });
    }

    private void resetRecognizer() {
        // 对于简单指令，通常不需要额外重置逻辑，因为 Vosk 会自动准备下一次识别。
        // 如果需要强制清空缓冲区可以考虑重启 service，但一般不需要。
    }
}
