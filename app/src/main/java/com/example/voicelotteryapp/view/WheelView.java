package com.example.voicelotteryapp.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import com.example.voicelotteryapp.model.Prize;

import java.util.List;

/**
 * 自定义转盘视图 (核心UI组件)
 * 负责绘制转盘、显示奖品分区、处理旋转动画。
 * 使用 Android 的 Canvas 进行底层绘制。
 */
public class WheelView extends View {

    // 画笔定义
    private Paint mArcPaint;    // 用于绘制每一个扇形的背景色
    private Paint mTextPaint;   // 用于绘制奖品文字
    private Paint mRimPaint;    // 用于绘制外部金色边框
    private Paint mLightPaint;  // 用于绘制边框上的跑马灯小圆点
    // private Paint mCenterPaint; // 中心盖帽画笔 (目前未使用，直接在该位置绘制了圆)

    // 尺寸与位置相关的变量
    private RectF mRange = new RectF(); // 扇区绘制的矩形范围
    private int mRadius;                // 转盘半径
    private int mCenter;                // 转盘中心坐标 (X=Y=mCenter)
    private int mPadding;               // 内缩边距，用于留出边框位置

    private List<Prize> mPrizes;        // 需要展示的奖品列表数据

    // 动画控制
    private ObjectAnimator mSpinAnimator; // 转动属性动画
    private boolean isSpinning = false;   // 当前是否正在旋转

    // 动画结束后的回调接口
    private OnFinishListener mDataListener;

    public interface OnFinishListener {
        void onFinish(Prize outcome); // 返回最终命中的奖品对象
    }

    // --- 标准的 View 构造函数 ---
    public WheelView(Context context) {
        this(context, null);
    }

    public WheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化画笔和属性配置
     */
    private void init() {
        // 1. 扇形画笔
        mArcPaint = new Paint();
        mArcPaint.setAntiAlias(true); // 抗锯齿
        mArcPaint.setStyle(Paint.Style.FILL);

        // 2. 文字画笔
        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(26); // 字体大小
        mTextPaint.setTextAlign(Paint.Align.RIGHT); // 右对齐（从圆周向圆心方向绘制）
        mTextPaint.setShadowLayer(2, 1, 1, Color.parseColor("#88000000")); // 文字阴影

        // 3. 外层边框画笔 (粉色系)
        mRimPaint = new Paint();
        mRimPaint.setAntiAlias(true);
        mRimPaint.setColor(Color.parseColor("#FFC0CB")); // 粉色
        mRimPaint.setStyle(Paint.Style.STROKE);
        mRimPaint.setStrokeWidth(40); // 边框粗细
        mRimPaint.setShadowLayer(4, 0, 2, Color.parseColor("#FF69B4")); // 边框阴影

        // 4. 跑马灯小灯画笔
        mLightPaint = new Paint();
        mLightPaint.setAntiAlias(true);
        mLightPaint.setColor(Color.parseColor("#87CEEB")); // 天蓝色小灯
        mLightPaint.setStyle(Paint.Style.FILL);
        mLightPaint.setShadowLayer(2, 0, 0, Color.WHITE); // 发光效果
    }

    public void setPrizes(List<Prize> prizes) {
        this.mPrizes = prizes;
        // 数据改变后，请求重绘界面
        invalidate(); 
    }
    
    public List<Prize> getPrizes() {
        return mPrizes;
    }

    public void setOnFinishListener(OnFinishListener listener) {
        this.mDataListener = listener;
    }

    /**
     * 测量 View 的大小
     * 确定转盘的圆心和半径
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec); // 强制为正方形
        int width = MeasureSpec.getSize(widthMeasureSpec);
        mCenter = width / 2;
        mRadius = width / 2;
        mPadding = 40; // 边距用于容纳边框和跑马灯，避免被切掉
        
        // 确定内部扇形的绘制范围
        mRange.set(mPadding, mPadding, width - mPadding, width - mPadding);
    }

    /**
     * 核心绘制逻辑
     * Android 系统会在绘制每一帧时调用此方法
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // -----------------------------------------------------
        // 1. 绘制外部边框环 (Outer Rim)
        // -----------------------------------------------------
        float rimWidth = 30f; // 实际绘制的边框宽度
        mRimPaint.setStrokeWidth(rimWidth);
        
        float outerMargin = 5f; // 防止边缘被裁切
        // 计算环的半径（注意 Paint.Style.STROKE 是以半径线为中心向两侧扩展）
        float rimRadius = (mRadius - outerMargin) - (rimWidth / 2f); 
        
        canvas.drawCircle(mCenter, mCenter, rimRadius, mRimPaint);
        
        // 为了美观，在环的内外两侧再画两条细线，增加层次感
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.parseColor("#FF80AB")); // 上深一点的粉色
        borderPaint.setStrokeWidth(3); 
        
        // 外描边
        canvas.drawCircle(mCenter, mCenter, rimRadius + rimWidth/2f - 1.5f, borderPaint); 
        // 内描边
        canvas.drawCircle(mCenter, mCenter, rimRadius - rimWidth/2f + 1.5f, borderPaint);

        // -----------------------------------------------------
        // 2. 绘制跑马灯小彩灯 (Lights)
        // -----------------------------------------------------
        int lightCount = 16; // 灯的数量
        for (int i = 0; i < lightCount; i++) {
            // 计算每个灯的角度
            double angle = Math.toRadians(360.0 / lightCount * i);
            // 计算每个灯的 (x,y) 坐标
            float x = (float) (mCenter + rimRadius * Math.cos(angle));
            float y = (float) (mCenter + rimRadius * Math.sin(angle));
            canvas.drawCircle(x, y, 6, mLightPaint);
        }

        if (mPrizes == null || mPrizes.isEmpty()) return;

        // -----------------------------------------------------
        // 3. 绘制扇形分区 (Sectors)
        // -----------------------------------------------------
        int count = mPrizes.size();
        
        // 分割线画笔
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(3); 
        linePaint.setStyle(Paint.Style.STROKE);

        // 初始角度 -90 度（即正上方）
        float currentStartAngle = -90;

        for (int i = 0; i < count; i++) {
            Prize prize = mPrizes.get(i);
            mArcPaint.setColor(prize.getColor());
            
            // 计算扫过的角度：视觉上我们采用均分策略（每个奖项面积相等）
            // 注意：虽然概率不同，但视觉面积相等是常见的转盘设计，增加了迷惑性和趣味性
            float sweepAngle = 360f / count;
            
            // 绘制扇形
            // useCenter=true 表示这是一块披萨形状（连到圆心）
            canvas.drawArc(mRange, currentStartAngle, sweepAngle, true, mArcPaint);

            // 绘制文字
            drawText(canvas, prize.getText(), currentStartAngle, sweepAngle);
            
            // 绘制分割线
            float rad = (float) Math.toRadians(currentStartAngle);
            float startX = mCenter;
            float startY = mCenter;
            float endX = (float) (mCenter + (mRadius - mPadding) * Math.cos(rad));
            float endY = (float) (mCenter + (mRadius - mPadding) * Math.sin(rad));
            canvas.drawLine(startX, startY, endX, endY, linePaint);
            
            currentStartAngle += sweepAngle;
        }
        
        // -----------------------------------------------------
        // 4. 绘制中心装饰圆点
        // -----------------------------------------------------
        Paint centerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerDotPaint.setColor(Color.WHITE);
        canvas.drawCircle(mCenter, mCenter, 8, centerDotPaint);
    }

    /**
     * 绘制弯曲并旋转的文字
     */
    private void drawText(Canvas canvas, String text, float startAngle, float sweepAngle) {
        float angle = startAngle + sweepAngle / 2; // 文字在扇形中间
        
        // 文字半径：比扇形半径略小一圈
        float textRadius = mRadius - mPadding - 20; 
        
        // 计算文字位置
        float x = (float) (mCenter + textRadius * Math.cos(Math.toRadians(angle)));
        float y = (float) (mCenter + textRadius * Math.sin(Math.toRadians(angle)));

        // 保存画布状态
        canvas.save();
        // 移动原点到文字位置
        canvas.translate(x, y);
        // 旋转画布，使文字方向指向圆心
        canvas.rotate(angle); 
        
        // 绘制文字
        canvas.drawText(text, 0, 8, mTextPaint); // +8 是为了垂直居中校正
        // 恢复画布状态
        canvas.restore();
    }

    /**
     * 开始旋转（匀速无限旋转）
     */
    public void start() {
        if (isSpinning) return;
        isSpinning = true;
        
        // 属性动画：旋转 View 本身 (rotation 属性)
        // 这种方式简单高效，但转的是整个 View，如果只需转盘子不转边框，需要改写 onDraw
        // 当前实现是整个转盘一起转
        mSpinAnimator = ObjectAnimator.ofFloat(this, "rotation", 0f, 360f);
        mSpinAnimator.setDuration(800); // 旋转一圈耗时
        mSpinAnimator.setRepeatCount(ValueAnimator.INFINITE); // 无限重复
        mSpinAnimator.setInterpolator(new LinearInterpolator()); // 匀速
        mSpinAnimator.start();
    }

    /**
     * 停止旋转并停在指定位置
     * @param targetIndex 目标奖品的索引
     */
    public void stop(int targetIndex) {
        if (!isSpinning || mPrizes == null) return;
        
        // 取消当前的匀速旋转动画
        mSpinAnimator.cancel();

        // 计算目标需要旋转到的最终角度
        // 我们希望目标扇形的中心线最终指向正上方 (-90度)
        
        float currentAngle = -90; // 这里的初始角度必须与 onDraw 中一致
        float targetSectorCenter = 0;
        
        // 找到目标扇形的中心角度（相对于转盘本身的 0 度位置）
        for (int i = 0; i < mPrizes.size(); i++) {
            float sweep = 360f / mPrizes.size();
            if (i == targetIndex) {
                targetSectorCenter = currentAngle + sweep / 2f;
                break;
            }
            currentAngle += sweep;
        }
        
        // 目标：让 targetSectorCenter 转到 -90 度的位置
        // 最终所需的 View 旋转角度 = -90 - targetSectorCenter
        float finalRotation = -90 - targetSectorCenter;
        
        // 规范化到正值区间
        while(finalRotation < 0) finalRotation += 360;
        
        // 多转几圈 (6圈)，增加悬念和缓冲感
        finalRotation += 360 * 6; 
        
        // 获取当前 View 的旋转角度
        float startRot = getRotation();
        float remainder = startRot % 360; // 当前在这一圈的什么位置
        float targetBase = finalRotation % 360; // 目标位置在这一圈的什么位置
        
        float diff = targetBase - remainder;
        if (diff < 0) diff += 360; // 补齐角度差，确保顺时针旋转
        
        // 最终的目标旋转角度 = 当前角度 + 差值 + 缓冲圈数
        float realDest = startRot + diff + 360 * 3; 
        
        // 启动减速停止动画
        ObjectAnimator stopAnim = ObjectAnimator.ofFloat(this, "rotation", startRot, realDest);
        stopAnim.setDuration(3000); // 3秒内慢慢停下
        stopAnim.setInterpolator(new DecelerateInterpolator()); // 减速插值器
        stopAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                if (mDataListener != null) {
                    mDataListener.onFinish(mPrizes.get(targetIndex));
                }
            }
        });
        stopAnim.start();
    }
}
