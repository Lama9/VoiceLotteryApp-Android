package com.example.voicelotteryapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.voicelotteryapp.manager.PrizeManager;
import com.example.voicelotteryapp.model.Prize;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 设置页面 Activity
 * 允许用户修改：
 * 1. 奖项配置（名称、概率、库存、颜色）
 * 2. 调试模式开关
 * 3. 语音播报开关
 * 4. 远程控制端口
 */
public class SettingsActivity extends AppCompatActivity {

    private RecyclerView rvPrizes;   // 显示奖品列表的滚动视图
    private PrizeAdapter mAdapter;   // 奖品列表适配器
    private List<Prize> mPrizes;     // 当前正在编辑的奖品数据副本
    private TextView tvTotalProb;    // 显示总概率的文本视图
    
    // 预定义颜色数组，添加新奖品时随机选用
    private int[] mColors = {
            Color.parseColor("#FF5252"), // 红
            Color.parseColor("#FF9800"), // 橙
            Color.parseColor("#FFC107"), // 琥珀
            Color.parseColor("#4CAF50"), // 绿
            Color.parseColor("#2196F3"), // 蓝
            Color.parseColor("#9C27B0")  // 紫
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // --- 初始化视图组件 ---
        rvPrizes = findViewById(R.id.rvPrizes);
        tvTotalProb = findViewById(R.id.tvTotalProb);
        
        // --- 1. 调试模式开关 ---
        android.widget.Switch switchDebug = findViewById(R.id.switchDebug);
        // 回显当前配置状态
        switchDebug.setChecked(PrizeManager.getInstance(this).isDebugMode());
        // 监听状态改变
        switchDebug.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrizeManager.getInstance(this).setDebugMode(isChecked);
        });

        // --- 2. 语音播报开关 ---
        android.widget.Switch switchSound = findViewById(R.id.switchSound);
        switchSound.setChecked(PrizeManager.getInstance(this).isSoundEnabled());
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrizeManager.getInstance(this).setSoundEnabled(isChecked);
            Toast.makeText(this, isChecked ? "语音播报已开启" : "语音播报已关闭", Toast.LENGTH_SHORT).show();
        });

        // --- 按钮初始化 ---
        Button btnAdd = findViewById(R.id.btnAdd);          // 添加奖项
        Button btnSave = findViewById(R.id.btnSave);        // 保存更改
        Button btnReset = findViewById(R.id.btnResetStock); // 重置库存
        android.widget.TextView tvServerInfo = findViewById(R.id.tvServerInfo);
        android.widget.EditText etPort = findViewById(R.id.etPort); // 端口输入框
        android.widget.ImageButton btnExit = findViewById(R.id.btnExitApp); // 退出应用
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);    // 返回
        
        // 退出程序逻辑
        btnExit.setOnClickListener(v -> {
            // finishAffinity 清空 Activity 栈，确保彻底退出
            finishAffinity();
            System.exit(0);
        });

        btnBack.setOnClickListener(v -> finish());
        
        // --- 端口与服务器信息 ---
        int currentPort = PrizeManager.getInstance(this).getServerPort();
        etPort.setText(String.valueOf(currentPort));
        
        // 显示本机 IP 地址，方便用户连接
        String ip = getLocalIpAddress();
        tvServerInfo.setText("Remote Control: http://" + ip + ":" + currentPort + "/api/status");

        // --- 加载奖品数据 ---
        // 注意：这里创建了一个新的 ArrayList 作为副本，避免直接修改单例中的源数据
        // 只有点击“保存”按钮时，才会将修改写回 PrizeManager
        mPrizes = new ArrayList<>(PrizeManager.getInstance(this).getConfiguredPrizes());
        
        mAdapter = new PrizeAdapter(mPrizes, new PrizeAdapter.OnPrizeUpdateListener() {
            @Override
            public void onUpdate() {
                // 当列表中任意数据变化（如概率修改）时，刷新总概率显示
                updateTotalProb();
            }

            @Override
            public void onDelete(int position) {
                // 删除奖项
                mPrizes.remove(position);
                mAdapter.notifyItemRemoved(position);
                updateTotalProb();
            }
        });

        rvPrizes.setLayoutManager(new LinearLayoutManager(this));
        rvPrizes.setAdapter(mAdapter);

        // --- 添加奖项逻辑 ---
        btnAdd.setOnClickListener(v -> {
            int randomColor = mColors[new Random().nextInt(mColors.length)];
            // 默认概率 10.0%
            mPrizes.add(new Prize("新奖项", randomColor, 10.0));
            mAdapter.notifyItemInserted(mPrizes.size() - 1); // 刷新列表
            updateTotalProb();
        });

        // --- 保存逻辑 ---
        btnSave.setOnClickListener(v -> saveSettings());
        
        // --- 重置库存逻辑 ---
        btnReset.setOnClickListener(v -> {
            // 调用 Manager 重置所有奖品的剩余库存为总库存
            PrizeManager.getInstance(this).resetStock();
            // 重新加载列表以显示最新数据
            mPrizes.clear();
            mPrizes.addAll(PrizeManager.getInstance(this).getConfiguredPrizes());
            mAdapter.notifyDataSetChanged();
            Toast.makeText(this, "库存已重置满额", Toast.LENGTH_SHORT).show();
        });

        updateTotalProb(); // 初始计算一次总概率
        
        // 显示版本号与构建时间
        TextView tvVersion = findViewById(R.id.tvVersionInfo);
        tvVersion.setText("Version: " + BuildConfig.VERSION_NAME + "  " + getString(R.string.build_time_dynamic));
    }

    /**
     * 保存所有设置并校验合法性
     */
    private void saveSettings() {
        // 1. 奖项名称校验
        int total = 0;
        for (Prize p : mPrizes) {
            if (p.getName().isEmpty()) {
                Toast.makeText(this, "奖项名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // 2. 端口号校验与保存
        android.widget.EditText etPort = findViewById(R.id.etPort);
        String portStr = etPort.getText().toString();
        try {
            int port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                Toast.makeText(this, "端口号建议在1024-65535之间", Toast.LENGTH_SHORT).show();
                return;
            }
            PrizeManager.getInstance(this).setServerPort(port);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口号无效", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 概率校验
        if (!validateProbabilities()) {
            Toast.makeText(this, "总概率必须为100%!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 4. 持久化保存
        PrizeManager.getInstance(this).savePrizes(mPrizes);
        Toast.makeText(this, "配置已保存 (重启生效)", Toast.LENGTH_SHORT).show();
        finish(); // 关闭设置页，返回主页
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
                    // 过滤非回环和 IPv4
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
     * 校验总概率是否合法
     * @return true=合法, false=超标
     */
    private boolean validateProbabilities() {
        double total = 0;
        for (Prize p : mPrizes) {
            total += p.getProbability();
        }
        
        // 保留两位小数显示
        String totalStr = String.format("%.2f", total);
        tvTotalProb.setText("总计: " + totalStr + "%");
        
        // 模糊比较：允许 100.001 这种微小浮点误差，但不能明显超过 100
        if (total > 100.001) { 
            tvTotalProb.setTextColor(Color.RED);
            return false;
        } else {
            // 小于等于 100 都是合法的
            // 因为 drawPrize 逻辑会自动填充剩余概率为“谢谢参与”
            tvTotalProb.setTextColor(Color.parseColor("#4CAF50")); // 绿色
            return true;
        }
    }

    /**
     * 仅刷新与显示总概率，不做拦截
     */
    private void updateTotalProb() {
        double total = 0;
        for (Prize p : mPrizes) {
            total += p.getProbability();
        }
        String totalStr = String.format("%.2f", total);
        tvTotalProb.setText("总计: " + totalStr + "%");
        
        if (total > 100.001) {
            tvTotalProb.setTextColor(Color.RED);
        } else {
            tvTotalProb.setTextColor(Color.parseColor("#4CAF50")); // Green
        }
    }
}
