package com.example.newpdr.DataClass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;

/**
 * - 采用逻辑坐标操作
 * - 动态网格标签显示
 * - 屏幕跟随：
 *   如果不触碰屏幕，屏幕可以自动跟随轨迹点；
 *   触碰屏幕后，需要手动调整视图，可以缩放和平移。
 */
public class PositionView extends View {
    private Paint axisPaint, gridPaint, pointPaint, textPaint;
    private float currentX = 0, currentY = 0; // 逻辑坐标（未缩放/平移）
    private float scaleFactor = 1.0f;         // 缩放因子
    private float offsetX = 0, offsetY = 0;     // 平移偏移量（逻辑坐标）

    private ArrayList<PointF> trailPoints = new ArrayList<>(); // 轨迹点（逻辑坐标）
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private Matrix transformMatrix = new Matrix(); // 变换矩阵
    private boolean enableScreenFollow = true;  // 屏幕跟随开关
    private boolean isUserInteracting = false;  // 用户正在交互标志

    // 如果设置为 true，则每次更新时仅显示最新的点（避免累积大量数据）
    private boolean onlyLatest = false;

    public PositionView(Context context) {
        super(context);
        init(context);
    }

    public PositionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // 初始化画笔
        axisPaint = new Paint();
        axisPaint.setColor(Color.BLACK);
        axisPaint.setStrokeWidth(3);

        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 10}, 0));

        pointPaint = new Paint();
        pointPaint.setColor(Color.RED);
        pointPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);

        // 手势检测器
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    // 重写 onSizeChanged，在视图大小确定时将原点设置到中心
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        offsetX = w / 2f;
        offsetY = h / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        // 应用矩阵变换（先平移后缩放）
        transformMatrix.reset();
        transformMatrix.postTranslate(offsetX, offsetY); // 逻辑坐标平移
        transformMatrix.postScale(scaleFactor, scaleFactor, getWidth() / 2f, getHeight() / 2f); // 以视图中心为缩放点
        canvas.concat(transformMatrix);

        // 绘制内容（全部使用逻辑坐标）
        drawGrid(canvas);
        drawAxes(canvas);
        drawTrail(canvas);
        drawCurrentPosition(canvas);

        canvas.restore();

        // 在屏幕坐标系绘制标签（不受矩阵变换影响）
        drawScreenLabels(canvas);
    }

    // 在屏幕坐标系绘制标签
    private void drawScreenLabels(Canvas canvas) {
        float logicalStep = 50; // 基础逻辑步长
        int labelInterval = (int)(5/scaleFactor); // 每5个网格显示标签
        float labelStep = logicalStep * labelInterval; // 实际标签步长

        // 获取可见区域边界（逻辑坐标）
        float[] visibleRect = {
                -offsetX - getWidth() / (2 * scaleFactor),
                -offsetY - getHeight() / (2 * scaleFactor),
                -offsetX + getWidth() / (2 * scaleFactor),
                -offsetY + getHeight() / (2 * scaleFactor)
        };

        // 绘制X轴标签（显示在Y=0的位置）
        float startX = (float) (Math.floor(visibleRect[0] / labelStep) * labelStep);
        float endX = (float) (Math.ceil(visibleRect[2] / labelStep) * labelStep);
        for (float x = startX; x <= endX; x += labelStep) {
            float[] screenPos = transformLogicalToScreen(x, 0);
            if (screenPos[0] > 50 && screenPos[0] < getWidth() - 50) {
                String text = String.valueOf((int) (x / logicalStep));
                canvas.drawText(text, screenPos[0], getHeight() - 20, textPaint);
            }
        }

        // 绘制Y轴标签（显示在X=0的位置）
        float startY = (float) (Math.floor(visibleRect[1] / labelStep) * labelStep);
        float endY = (float) (Math.ceil(visibleRect[3] / labelStep) * labelStep);
        for (float y = startY; y <= endY; y += labelStep) {
            float[] screenPos = transformLogicalToScreen(0, y);
            if (screenPos[1] > 50 && screenPos[1] < getHeight() - 50) {
                String text = String.valueOf(-(int) (y / logicalStep));
                canvas.drawText(text, 20, screenPos[1], textPaint);
            }
        }
    }

    // 逻辑坐标转换到屏幕坐标
    private float[] transformLogicalToScreen(float logicalX, float logicalY) {
        Matrix matrix = new Matrix();
        matrix.set(transformMatrix);
        matrix.postConcat(getMatrix());
        float[] points = {logicalX, logicalY};
        matrix.mapPoints(points);
        return points;
    }

    // 绘制网格（逻辑坐标）
    private void drawGrid(Canvas canvas) {
        float step = 50;
        for (int i = -2000; i <= 2000; i++) {
            canvas.drawLine(i * step, -100000, i * step, 100000, gridPaint);
            canvas.drawLine(-100000, i * step, 100000, i * step, gridPaint);
        }
    }

    // 绘制坐标轴（逻辑坐标）
    private void drawAxes(Canvas canvas) {
        canvas.drawLine(-100000, 0, 100000, 0, axisPaint);
        canvas.drawLine(0, -100000, 0, 100000, axisPaint);
    }

    // 绘制轨迹点（逻辑坐标）
    private void drawTrail(Canvas canvas) {
        // 如果只显示最新点，则只绘制最后一个点
        if (onlyLatest && !trailPoints.isEmpty()) {
            PointF p = trailPoints.get(trailPoints.size() - 1);
            canvas.drawCircle(p.x, p.y, 3, pointPaint);
        } else {
            // 否则绘制全部轨迹点
            for (PointF p : trailPoints) {
                canvas.drawCircle(p.x, p.y, 5, pointPaint);
            }
        }
    }

    // 绘制当前位置（逻辑坐标）
    private void drawCurrentPosition(Canvas canvas) {
        canvas.drawCircle(currentX, currentY, 10, pointPaint);
    }

    /**
     * 更新位置。参数 x 和 y 为物理坐标（单位：米）。
     * 这里我们将物理坐标乘以转换因子（50）转换为逻辑坐标，
     * 如果 onlyLatest 为 true，则清空之前的轨迹数据，只保留最新点；
     * 如果为 false，则最多保留 50 个点。
     */
    public void updatePosition(double x, double y) {
        currentX = (float) (x * 50);
        currentY = (float) (y * 50);
        if (onlyLatest) {
            trailPoints.clear();
        }
        trailPoints.add(new PointF(currentX, currentY));

        // 屏幕跟随逻辑
        if (enableScreenFollow && !isUserInteracting) {
            float targetOffsetX = getWidth() / (2 * scaleFactor) - currentX;
            float targetOffsetY = getHeight() / (2 * scaleFactor) - currentY;
            offsetX += (targetOffsetX - offsetX) * 0.2f;
            offsetY += (targetOffsetY - offsetY) * 0.2f;
        }

        if (!onlyLatest && trailPoints.size() > 5000) {
            trailPoints.remove(0);
        }
        postInvalidate();
    }

    // 手势处理
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    // 缩放手势监听
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            isUserInteracting = true;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            isUserInteracting = false;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float[] point = {focusX, focusY};
            Matrix inverse = new Matrix();
            transformMatrix.invert(inverse);
            inverse.mapPoints(point);
            float centerX = point[0];
            float centerY = point[1];

            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.05f, Math.min(scaleFactor, 5.0f));

            offsetX += (centerX - offsetX) * (1 - 1 / detector.getScaleFactor());
            offsetY += (centerY - offsetY) * (1 - 1 / detector.getScaleFactor());

            invalidate();
            return true;
        }
    }

    // 拖动手势监听
    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            isUserInteracting = true;
            offsetX -= distanceX / scaleFactor;
            offsetY -= distanceY / scaleFactor;
            invalidate();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
    }
}
