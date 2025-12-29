package com.example.voicelotteryapp.model;

/**
 * 奖品数据模型类 (POJO)
 * 存储单个奖项的所有配置信息。
 */
public class Prize {
    private String name;        // 奖品名称 (显示在转盘扇区上)
    private int iconResId;      // 奖品图标资源ID (目前未使用，保留字段)
    private int color;          // 扇形背景颜色 (ARGB整数)
    private String text;        // 显示的文字 (通常与name一致)
    private double probability; // 中奖概率 (0-100, 支持小数)
    private int totalCount;     // 总库存数量 (-1表示无限库存)
    private int remainingCount; // 剩余库存数量 (每次中奖后减1)

    // 全参构造函数
    public Prize(String name, int color, double probability, int totalCount) {
        this.name = name;
        this.color = color;
        this.text = name;
        this.probability = probability;
        this.totalCount = totalCount;
        this.remainingCount = totalCount; // 初始剩余等于总数
    }

    // 兼容旧代码的构造函数
    public Prize(String name, int color, double probability) {
        this(name, color, probability, -1);
    }

    // 简单构造函数
    public Prize(String name, int color) {
        this(name, color, 0);
    }

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.text = name;
    }

    public int getColor() {
        return color;
    }
    
    public void setColor(int color) {
        this.color = color;
    }

    public String getText() {
        return text;
    }
    
    public double getProbability() {
        return probability;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
        // 如果修改了总数，为了逻辑简单，重置剩余数量为新的总数
        // 实际应用中可能需要更复杂的逻辑（如保留已消耗的数量）
        this.remainingCount = totalCount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(int remainingCount) {
        this.remainingCount = remainingCount;
    }
    
    /**
     * 判断是否为无限库存
     */
    public boolean isUnlimited() {
        return totalCount == -1;
    }
}
