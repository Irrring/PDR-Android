package com.example.newpdr.DataClass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 改进后的磁强计可视化视图
 * 支持平移、缩放，并以二维正交投影展示采集到的磁强计 3D 数据点，
 * 使用热力图风格将 z 值映射为颜色。
 */
public class Magnetometer3DView extends View {
    private Paint axisPaint, gridPaint, pointPaint, textPaint;
    private float scaleFactor = 1.0f;
    private float offsetX = 0, offsetY = 0;     // 平移偏移量
    private List<float[]> samplePoints = new ArrayList<>(); // 存储磁强计采样数据，每个点为 {x, y, z}

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private Matrix transformMatrix = new Matrix();

    public Magnetometer3DView(Context context) {
        super(context);
        init(context);
    }

    public Magnetometer3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public Magnetometer3DView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        axisPaint = new Paint();
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(3);

        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1);
        gridPaint.setStyle(Paint.Style.STROKE);

        pointPaint = new Paint();
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    // 重写 onSizeChanged 使得初始偏移设置为视图中心
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        offsetX = w / 2f;
        offsetY = h / 2f;
    }

    /**
     * 外部调用更新采样数据
     */
    public void updateSamples(List<float[]> samples) {
        this.samplePoints = samples;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 重置矩阵，先平移，再缩放
        transformMatrix.reset();
        transformMatrix.postTranslate(offsetX, offsetY);
        transformMatrix.postScale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f);
        canvas.concat(transformMatrix);

        drawGrid(canvas);
        drawAxes(canvas);
        drawSamples(canvas);
    }

    private void drawGrid(Canvas canvas) {
        float step = 50;
        int count = 20;
        for (int i = -count; i <= count; i++) {
            canvas.drawLine(i * step, -count * step, i * step, count * step, gridPaint);
            canvas.drawLine(-count * step, i * step, count * step, i * step, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas) {
        canvas.drawLine(-1000, 0, 1000, 0, axisPaint);
        canvas.drawLine(0, -1000, 0, 1000, axisPaint);
    }

    private void drawSamples(Canvas canvas) {
        if (samplePoints.isEmpty()) {
            return;
        }
        // 计算 z 值的最小值和最大值，用于归一化
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] point : samplePoints) {
            if (point[2] < minZ) minZ = point[2];
            if (point[2] > maxZ) maxZ = point[2];
        }
        if (Math.abs(maxZ - minZ) < 0.001f) {
            maxZ = minZ + 0.001f;
        }
        // 绘制每个采样点，使用热力图颜色映射
        for (float[] point : samplePoints) {
            float x = point[0] * 8; // 适当缩放
            float y = point[1] * 8;
            float z = point[2];
            int color = getHeatMapColor(z, minZ, maxZ);
            pointPaint.setColor(color);
            canvas.drawCircle(x, y, 5, pointPaint);
        }
    }

    /**
     * 根据 z 值映射到热力图颜色。
     * 这里使用蓝->青->绿->黄->红的梯度映射。
     *
     * @param value 当前 z 值
     * @param minValue z 的最小值
     * @param maxValue z 的最大值
     * @return 颜色值
     */
    private int getHeatMapColor(float value, float minValue, float maxValue) {
        float normalized = (value - minValue) / (maxValue - minValue);
        normalized = Math.max(0, Math.min(1, normalized));
        float r, g, b;
        if (normalized <= 0.25f) {
            // 蓝到青： (0, 0, 255) -> (0, 255, 255)
            float t = normalized / 0.25f;
            r = 0;
            g = t * 255;
            b = 255;
        } else if (normalized <= 0.5f) {
            // 青到绿： (0, 255, 255) -> (0, 255, 0)
            float t = (normalized - 0.25f) / 0.25f;
            r = 0;
            g = 255;
            b = 255 - t * 255;
        } else if (normalized <= 0.75f) {
            // 绿到黄： (0, 255, 0) -> (255, 255, 0)
            float t = (normalized - 0.5f) / 0.25f;
            r = t * 255;
            g = 255;
            b = 0;
        } else {
            // 黄到红： (255, 255, 0) -> (255, 0, 0)
            float t = (normalized - 0.75f) / 0.25f;
            r = 255;
            g = 255 - t * 255;
            b = 0;
        }
        return Color.rgb((int) r, (int) g, (int) b);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            offsetX -= distanceX;
            offsetY -= distanceY;
            invalidate();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }
}
