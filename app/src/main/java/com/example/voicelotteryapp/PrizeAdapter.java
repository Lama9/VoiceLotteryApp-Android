package com.example.voicelotteryapp;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.voicelotteryapp.model.Prize;

import java.util.List;

/**
 * 奖品列表适配器 (RecyclerView Adapter)
 * 负责在设置页面中展示和编辑奖品列表。
 * 主要功能：
 * 1. 渲染每一个奖品条目 (item_prize_setting.xml)
 * 2. 处理输入框的数据绑定（双向绑定思想）
 * 3. 处理颜色选择和删除操作
 */
public class PrizeAdapter extends RecyclerView.Adapter<PrizeAdapter.ViewHolder> {

    private List<Prize> mPrizes; // 数据源
    private OnPrizeUpdateListener mListener; // 回调接口，通知外部数据变更
    
    // 预定义的颜色列表，供用户选择
    private final int[] mColors = {
            Color.parseColor("#FF5252"), // 红色
            Color.parseColor("#FF9800"), // 橙色
            Color.parseColor("#FFC107"), // 琥珀色
            Color.parseColor("#4CAF50"), // 绿色
            Color.parseColor("#2196F3"), // 蓝色
            Color.parseColor("#9C27B0"), // 紫色
            Color.parseColor("#E91E63"), // 粉色
            Color.parseColor("#00BCD4")  // 青色
    };

    /**
     * 定义一个回调接口，当数据更新或删除了某项时通知 Activity
     */
    public interface OnPrizeUpdateListener {
        void onUpdate();           // 数据（如概率）更新
        void onDelete(int position); // 请求删除某一行
    }

    public PrizeAdapter(List<Prize> prizes, OnPrizeUpdateListener listener) {
        this.mPrizes = prizes;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载 item 布局文件
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_prize_setting, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Prize prize = mPrizes.get(position);
        
        // 重要：在重新绑定数据前，必须移除旧的 TextWatcher，否则会导致数据错乱或死循环
        // 因为 RecyclerView 会复用 ViewHolder
        if (holder.nameWatcher != null) holder.etName.removeTextChangedListener(holder.nameWatcher);
        if (holder.probWatcher != null) holder.etProb.removeTextChangedListener(holder.probWatcher);
        if (holder.countWatcher != null) holder.etCount.removeTextChangedListener(holder.countWatcher);

        // 设置当前值
        holder.etName.setText(prize.getName());
        holder.etProb.setText(String.valueOf(prize.getProbability()));
        holder.etCount.setText(String.valueOf(prize.getTotalCount()));
        
        // 设置库存状态显示
        String status = prize.isUnlimited() ? "∞" : "余:" + prize.getRemainingCount();
        holder.tvStockStatus.setText(status);
        // 如果库存为0且非无限模式，标红显示
        holder.tvStockStatus.setTextColor(prize.getRemainingCount() == 0 && !prize.isUnlimited() ? Color.RED : Color.GRAY);
        
        // 渲染颜色圆形指示器
        updateColorView(holder.vColor, prize.getColor());

        // 颜色选择点击事件
        holder.vColor.setOnClickListener(v -> {
            showColorPicker(holder.itemView.getContext(), prize, holder.vColor);
        });

        // --- 重新添加 TextWatcher 监听输入 ---
        
        // 1. 监听奖品名称变化
        holder.nameWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                prize.setName(s.toString());
            }
        };
        holder.etName.addTextChangedListener(holder.nameWatcher);

        // 2. 监听概率变化 (支持小数)
        holder.probWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String input = s.toString();
                    if (input.isEmpty()) {
                        prize.setProbability(0);
                    } else {
                        // 解析 double
                        prize.setProbability(Double.parseDouble(input));
                    }
                    // 通知外部更新总概率
                    mListener.onUpdate();
                } catch (NumberFormatException e) {
                    prize.setProbability(0); // 格式错误当 0 处理
                }
            }
        };
        holder.etProb.addTextChangedListener(holder.probWatcher);
        
        // 3. 监听总库存数量变化
        holder.countWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String input = s.toString();
                    if (input.isEmpty() || input.equals("-")) {
                        // 正在输入负号等情况，暂不处理
                    } else {
                        prize.setTotalCount(Integer.parseInt(input));
                    }
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }
        };
        holder.etCount.addTextChangedListener(holder.countWatcher);

        // 删除按钮点击事件
        holder.btnDelete.setOnClickListener(v -> {
            // 使用 getBindingAdapterPosition 获取当前准确位置
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                mListener.onDelete(pos);
            }
        });
    }

    /**
     * 绘制圆形的颜色指示器
     */
    private void updateColorView(View view, int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL); // 形状为椭圆(圆)
        shape.setColor(color);                 // 填充颜色
        shape.setStroke(2, Color.LTGRAY);      // 浅灰色边框
        view.setBackground(shape);
    }
    
    /**
     * 弹出颜色选择对话框
     */
    private void showColorPicker(android.content.Context context, Prize prize, View viewToUpdate) {
        String[] colorNames = {"红色", "橙色", "琥珀色", "绿色", "蓝色", "紫色", "粉色", "青色"};
        
        new AlertDialog.Builder(context)
                .setTitle("选择颜色")
                .setItems(colorNames, (dialog, which) -> {
                    int selectedColor = mColors[which];
                    // 更新模型数据
                    prize.setColor(selectedColor);
                    // 更新UI显示
                    updateColorView(viewToUpdate, selectedColor);
                })
                .show();
    }

    @Override
    public int getItemCount() {
        return mPrizes.size();
    }

    /**
     * ViewHolder 模式：缓存视图引用，提高列表滚动性能
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etName;
        EditText etProb;
        EditText etCount;
        android.widget.TextView tvStockStatus;
        View vColor;
        ImageButton btnDelete;
        TextWatcher nameWatcher;
        TextWatcher probWatcher;
        TextWatcher countWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            etName = itemView.findViewById(R.id.etName);
            etProb = itemView.findViewById(R.id.etProb);
            etCount = itemView.findViewById(R.id.etCount);
            tvStockStatus = itemView.findViewById(R.id.tvStockStatus);
            vColor = itemView.findViewById(R.id.vColor);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }

    /**
     * 简化的 TextWatcher 抽象类，避免每次都实现所有方法
     */
    abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
