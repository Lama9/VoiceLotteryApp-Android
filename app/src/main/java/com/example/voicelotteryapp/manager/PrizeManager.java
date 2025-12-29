package com.example.voicelotteryapp.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import com.example.voicelotteryapp.model.Prize;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 奖品管理器 (单例模式)
 * 负责应用的数据核心逻辑：
 * 1. 数据的持久化存储（保存到 SharedPreferences）
 * 2. 数据的读取与解析（JSON -> Object）
 * 3. 核心抽奖算法（加权随机 + 库存降级）
 * 4. 全局配置管理（端口、开关等）
 */
public class PrizeManager {
    // SharedPreferences 键名常量
    private static final String PREF_NAME = "lottery_prefs";
    private static final String KEY_PRIZES = "prizes_json";
    private static final String KEY_DEBUG = "debug_mode";
    
    private static PrizeManager instance;
    private SharedPreferences sharedPreferences;

    // 私有构造函数，防止外部直接 new
    private PrizeManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    // --- 全局配置 ---

    // 调试模式开关：开启后会在主界面显示日志和库存
    public void setDebugMode(boolean enabled) {
        sharedPreferences.edit().putBoolean(KEY_DEBUG, enabled).apply();
    }
    
    public boolean isDebugMode() {
        return sharedPreferences.getBoolean(KEY_DEBUG, false);
    }
    
    // 局域网控制端口
    public int getServerPort() {
        return sharedPreferences.getInt("server_port", 18888);
    }
    
    public void setServerPort(int port) {
        sharedPreferences.edit().putInt("server_port", port).apply();
    }
    
    // 语音播报总开关
    public boolean isSoundEnabled() {
        return sharedPreferences.getBoolean("sound_enabled", true);
    }
    
    public void setSoundEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean("sound_enabled", enabled).apply();
    }

    // 获取单例实例
    public static synchronized PrizeManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrizeManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 保存奖品列表配置到本地存储
     * 将 List<Prize> 序列化为 JSON 字符串保存
     */
    public void savePrizes(List<Prize> prizes) {
        JSONArray jsonArray = new JSONArray();
        for (Prize p : prizes) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", p.getName());
                obj.put("color", p.getColor());
                obj.put("probability", p.getProbability());
                obj.put("totalCount", p.getTotalCount());
                obj.put("remainingCount", p.getRemainingCount());
                jsonArray.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // commit() 是同步写入，apply() 是异步写入。这里用 apply 即可。
        sharedPreferences.edit().putString(KEY_PRIZES, jsonArray.toString()).apply();
    }

    /**
     * 读取配置的奖品列表
     * 如果没有配置过，则返回默认的预设数据
     */
    public List<Prize> getConfiguredPrizes() {
        String json = sharedPreferences.getString(KEY_PRIZES, null);
        List<Prize> list = new ArrayList<>();
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    Prize p = new Prize(
                            obj.optString("name"),
                            obj.optInt("color"),
                            obj.optDouble("probability"),
                            obj.optInt("totalCount", -1) // 默认值为无限库存(-1)
                    );
                    p.setRemainingCount(obj.optInt("remainingCount", p.getTotalCount()));
                    list.add(p);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        // 如果数据为空，加载默认预设
        if (list.isEmpty()) {
            list.add(new Prize("一等奖", Color.parseColor("#FFF176"), 10.0, 1)); // 淡黄
            list.add(new Prize("二等奖", Color.parseColor("#F48FB1"), 20.0, 5)); // 淡粉
            list.add(new Prize("三等奖", Color.parseColor("#AED581"), 30.0, 10)); // 淡绿
            list.add(new Prize("惊喜奖", Color.parseColor("#FFB74D"), 40.0, -1)); // 淡橙
        }
        return list;
    }

    /**
     * 获取用于显示的奖品列表
     * 关键逻辑：如果配置的奖品总概率小于 100%，会自动填充一个“谢谢参与”奖项
     */
    public List<Prize> getDisplayPrizes() {
        List<Prize> configured = getConfiguredPrizes();
        List<Prize> displayList = new ArrayList<>(configured);

        double totalProb = 0;
        for (Prize p : configured) {
            totalProb += p.getProbability();
        }

        if (totalProb < 100) {
            // 自动补齐“谢谢参与”
            double remaining = 100.0 - totalProb;
            // 四舍五入保留两位小数，避免浮点数精度问题 (如 99.999999)
            remaining = Math.round(remaining * 100.0) / 100.0;
            displayList.add(new Prize("谢谢参与", Color.parseColor("#9E9E9E"), remaining, -1)); 
        }
        
        return displayList;
    }
    
    /**
     * 检查某个奖品是否有库存
     * @return true:有库存或无限库存; false:库存耗尽
     */
    public boolean hasStock(Prize prize) {
        if (prize.isUnlimited()) return true;
        return prize.getRemainingCount() > 0;
    }

    /**
     * 扣减库存
     */
    public void decreaseStock(Prize prize) {
        if (prize.isUnlimited()) return;
        
        int current = prize.getRemainingCount();
        if (current > 0) {
            prize.setRemainingCount(current - 1);
            // 必须立即持久化保存更新后的状态
            updatePrizeState(prize);
        }
    }
    
    /**
     * 更新单个奖品的状态并保存
     */
    private void updatePrizeState(Prize updatedPrize) {
        List<Prize> currentList = getConfiguredPrizes();
        for (Prize p : currentList) {
            // 目前通过名称匹配（简单实现）
            if (p.getName().equals(updatedPrize.getName())) {
                p.setRemainingCount(updatedPrize.getRemainingCount());
                break;
            }
        }
        savePrizes(currentList);
    }
    
    /**
     * 获取兜底奖项 (Consolation Prize)
     * 当原本抽中的大奖库存耗尽时，系统需要给出一个“安慰奖”。
     * 策略：从当前列表中寻找任意一个库存无限的奖项。
     */
    public Prize getConsolationPrize() {
        List<Prize> pool = getDisplayPrizes();
        List<Prize> candidates = new ArrayList<>();
        
        for (Prize p : pool) {
             if (p.isUnlimited()) {
                 candidates.add(p);
             }
        }
        
        if (!candidates.isEmpty()) {
            // 如果有多个无限奖项，随机选一个增加随机性
            return candidates.get(new Random().nextInt(candidates.size()));
        }

        // 极端兜底（如果没有配置任何无限奖项）
        return new Prize("安慰奖", Color.parseColor("#9E9E9E"), 0, -1);
    }

    /**
     * 重置库存
     * 将所有奖品的剩余数量(Remaining)恢复为总数量(Total)
     */
    public void resetStock() {
        List<Prize> prizes = getConfiguredPrizes();
        for (Prize p : prizes) {
            p.setRemainingCount(p.getTotalCount());
        }
        savePrizes(prizes);
        Log.i("VoiceLottery", "Inventory Reset: All prizes restored to full stock.");
    }

    // 抽奖结果封装类
    public static class DrawResult {
        public final Prize original; // 原始命中目标（按概率随机的结果）
        public final Prize actual;   // 实际发放结果（经过库存检查后的结果）

        public DrawResult(Prize original, Prize actual) {
            this.original = original;
            this.actual = actual;
        }
    }

    /**
     * 核心抽奖算法：带库存检查的加权随机
     * @return DrawResult 包含原始目标和最终结果
     */
    public DrawResult drawPrize() {
        List<Prize> pool = getDisplayPrizes();
        if (pool.isEmpty()) return null;

        Log.d("VoiceLottery", "--- New Draw Started (开始新一轮抽奖) ---");
        Log.d("VoiceLottery", "当前库存快照:");
        double totalWeight = 0;
        for (Prize p : pool) {
            totalWeight += p.getProbability();
            String stock = p.isUnlimited() ? "无限" : (p.getRemainingCount() + "/" + p.getTotalCount());
            Log.d("VoiceLottery", " - " + p.getName() + ": 概率=" + p.getProbability() + "%, 库存=" + stock);
        }
        
        // 1. 掷骰子（0.0 到 总权重）
        Prize selectedPrize = null;
        if (totalWeight > 0) {
            double random = new Random().nextDouble() * totalWeight;
            Log.d("VoiceLottery", "随机数: " + random + " / " + totalWeight);
            
            // 累加权重查找命中区间
            double currentWeight = 0;
            for (Prize p : pool) {
                currentWeight += p.getProbability();
                if (random < currentWeight) {
                    selectedPrize = p;
                    break;
                }
            }
        }
        
        // 兜底防止计算误差
        if (selectedPrize == null) selectedPrize = pool.get(0);
        Log.d("VoiceLottery", "原始命中目标: " + selectedPrize.getName());
        
        Prize finalPrize = selectedPrize;

        // 2. 库存检查（公平性逻辑核心）
        if (!hasStock(selectedPrize)) {
            Log.w("VoiceLottery", "库存不足! 奖品 '" + selectedPrize.getName() + "' 已抽完。触发降级策略。");
            finalPrize = getConsolationPrize();
        }
        
        // 3. 扣减库存（确认发放）
        decreaseStock(finalPrize);
        Log.d("VoiceLottery", "最终发放: " + finalPrize.getName() + "。库存已更新。");
        
        return new DrawResult(selectedPrize, finalPrize);
    }
}
