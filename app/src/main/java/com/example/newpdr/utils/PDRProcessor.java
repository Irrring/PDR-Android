package com.example.newpdr.utils;

import android.util.Log;
import com.example.newpdr.DataClass.*;
import java.util.LinkedList;

/**
 * 行人航迹推算核心处理器
 * 功能：航向角计算、脚步检测、位置推算
 * 说明：本实现中ESKF仅建模航向角误差，不估计陀螺仪偏置误差。
 *       测量更新时，航向角误差定义为： error = nominalYaw - magnetometerYaw
 *       然后用1维卡尔曼滤波估计该误差，补偿到nominalYaw中，再将误差状态清零。
 */
public class PDRProcessor {
    //region 常量配置
    private static final int ACC_WINDOW_SIZE = 20;       // 加速度滑动窗口大小
    private static final long MIN_STEP_INTERVAL = 300;     // 最小步间间隔(ms)
    private static final double BASE_STEP_LENGTH = 0.7;    // 基础步长(m)
    private static final float STEP_THRESHOLD = 10.8f;     // 脚步检测阈值(m/s²)
    private static final float MAGNETIC_DECLINATION = (float) Math.toRadians(-3.3); // 地磁偏角
    // 过程噪声（针对航向角误差的1维状态）
    private static final double Q_heading = 1e-4;
    // 磁力计观测噪声
    private static final double R = 0.05;
    //endregion

    //region 状态变量
    private final LinkedList<AccelData> accelWindow = new LinkedList<>();
    private long lastStepTime = 0;
    private long lastGyroTime = 0;
    // nominalYaw：由陀螺仪积分得到的航向角（未经校正）
    private double nominalYaw = 0;
    // filteredYaw：最终输出的校正后航向角
    private double filteredYaw = 0;
    private double[] position = {0, 0};
    private float[] lastAccelData = new float[3];
    private float[] lastMagData = new float[3];
    private boolean initialHeadingSet = false;
    private int stepCount = 0;

    // 1维卡尔曼滤波协方差，表示航向角误差的方差
    private double P_heading = 1e-3;
    //endregion

    //region 公开接口
    public void processAccelerometer(long timestamp, float[] values) {
        if (values == null || values.length < 3  || !initialHeadingSet) return;

        float magnitude = (float) Math.sqrt(values[0]*values[0] + values[1]*values[1] + values[2]*values[2]);
        accelWindow.add(new AccelData(timestamp, magnitude, values));
        if (accelWindow.size() > ACC_WINDOW_SIZE) {
            accelWindow.removeFirst();
        }
        if (accelWindow.size() == ACC_WINDOW_SIZE) {
            detectStep();
        }
        lastAccelData = values.clone();
    }

    public void processGyroscope(long timestamp, float[] values) {
        if (values == null || values.length < 3 || lastAccelData == null || !initialHeadingSet) return;

        if (lastGyroTime == 0) {
            lastGyroTime = timestamp;
            return;
        }
        float dt = (timestamp - lastGyroTime) / 1000.0f;

        // 调平：利用加速度计数据计算 roll 和 pitch
        float roll = (float) Math.atan2(-lastAccelData[1], -lastAccelData[2]);
        float pitch = (float) Math.atan2(lastAccelData[0],
                (float) Math.sqrt(lastAccelData[1]*lastAccelData[1] + lastAccelData[2]*lastAccelData[2]));

        // 获取调平后的陀螺仪 Z 轴分量
        float horizon_gyroZ = (float)(- Math.sin(pitch) * values[0]
                + Math.sin(roll)*Math.cos(pitch)*values[1]
                + Math.cos(roll)*Math.cos(pitch)*values[2]);

        // 积分更新：直接用调平后的陀螺仪数据积分得到 nominalYaw
        nominalYaw = normalizeAngle(nominalYaw + horizon_gyroZ * dt);

        // 预测阶段：简单地将协方差增加过程噪声
        P_heading += Q_heading;

        // 在未进行磁力计更新前，输出即为 nominalYaw
        filteredYaw = nominalYaw;

//        Log.d("PDRProcessor", "Gyro update: dt=" + dt +
//                ", new nominalYaw=" + filteredYaw );

        lastGyroTime = timestamp;
    }

    public void processMagnetometer(float[] values) {
        if (values == null || values.length < 3) return;

        lastMagData = values.clone();
        if (!initialHeadingSet && lastAccelData != null) {
            // 初始设置：用磁力计确定初始航向
            nominalYaw = calculateYawFromMagnetometer();
            filteredYaw = nominalYaw;
            initialHeadingSet = true;
            P_heading = 1e-3; // 重置协方差
            //Log.d("PDRProcessor", "Initial heading set: magnetometer yaw=" + filteredYaw);
        } else if (lastAccelData != null) {
            float magneticYaw = calculateYawFromMagnetometer();
            updateHeadingFromMagnetometer(magneticYaw);
        }
    }

    public double[] getCurrentPosition() {
        return position.clone();
    }

    public double getCurrentYaw() {
        return filteredYaw;
    }

    public void reset() {
        accelWindow.clear();
        position = new double[]{0, 0};
        initialHeadingSet = false;
        stepCount = 0;
        lastGyroTime = 0;
        nominalYaw = 0;
        filteredYaw = 0;
        P_heading = 1e-3;
    }

    public int getStepCount() {
        return stepCount;
    }
    //endregion

    //region 核心算法
    private void detectStep() {
        if (accelWindow.size() != ACC_WINDOW_SIZE) return;
        float[] smoothedMagnitudes = new float[ACC_WINDOW_SIZE];
        float[] kernel = {0.06136f, 0.24477f, 0.38774f, 0.24477f, 0.06136f};
        for (int i = 2; i < ACC_WINDOW_SIZE - 2; i++) {
            float sum = 0;
            for (int j = -2; j <= 2; j++) {
                sum += kernel[j + 2] * accelWindow.get(i + j).magnitude;
            }
            smoothedMagnitudes[i] = sum;
        }
        int targetIndex = ACC_WINDOW_SIZE / 2;
        float currentMax = smoothedMagnitudes[targetIndex];
        boolean isMaximum = true;
        for (int i = 0; i < ACC_WINDOW_SIZE; i++) {
            if (smoothedMagnitudes[i] > currentMax) {
                isMaximum = false;
                break;
            }
        }
        boolean overThreshold = smoothedMagnitudes[targetIndex] > STEP_THRESHOLD;
        boolean validInterval = (accelWindow.get(targetIndex).timestamp - lastStepTime) >= MIN_STEP_INTERVAL;
        if (isMaximum && overThreshold && validInterval) {
            updatePosition();
            stepCount++;
            lastStepTime = accelWindow.get(targetIndex).timestamp;
        }
    }

    /**
     * 利用磁力计测量更新航向角。
     * 观测模型：磁力计测得的航向角 = nominalYaw - headingError
     * 其中 headingError 定义为 nominalYaw - magnetometerYaw。
     * 更新步骤：
     * 1. 计算误差： error = nominalYaw - magnetometerYaw
     * 2. 计算卡尔曼增益 K = P_heading / (P_heading + R)
     * 3. 估计航向误差： estimatedError = K * error
     * 4. 补偿航向： nominalYaw = nominalYaw - estimatedError
     * 5. 更新协方差： P_heading = (1 - K) * P_heading
     * 6. 将 filteredYaw 设置为校正后的 nominalYaw
     * 7. 日志打印更新信息
     */
    private void updateHeadingFromMagnetometer(float magneticYaw) {
        double error = normalizeAngle(nominalYaw - magneticYaw);
        double S = P_heading + R;
        double K = P_heading / S;
        double estimatedError = K * error;

        nominalYaw = normalizeAngle(nominalYaw - estimatedError);
        P_heading = (1 - K) * P_heading;
        filteredYaw = nominalYaw;

//        Log.d("PDRProcessor", "Magnetometer update: magnetYaw=" + magneticYaw +
//                ", error=" + error);
    }

    private float calculateYawFromMagnetometer() {
        float roll = (float) Math.atan2(-lastAccelData[1], -lastAccelData[2]);
        float pitch = (float) Math.atan2(lastAccelData[0],
                (float) Math.sqrt(lastAccelData[1]*lastAccelData[1] + lastAccelData[2]*lastAccelData[2]));
        float mx = lastMagData[0] * (float)Math.cos(pitch) +
                lastMagData[1] * (float)Math.sin(roll) * (float)Math.sin(pitch) +
                lastMagData[2] * (float)Math.cos(roll) * (float)Math.sin(pitch);
        float my = lastMagData[1] * (float)Math.cos(roll) -
                lastMagData[2] * (float)Math.sin(roll);
        return (float)normalizeAngle((float)-Math.atan2(my, mx) + MAGNETIC_DECLINATION);
    }

    private void updatePosition() {
        double stepLength = estimateStepLength();
        position[0] += stepLength * Math.cos(filteredYaw); // 北向(Y)
        position[1] += stepLength * Math.sin(filteredYaw); // 东方(X)

        Log.d("PDRProcessor","X: " + position[1] + " Y: " + position[0]);

    }

    private double estimateStepLength() {
        return BASE_STEP_LENGTH;
    }

    /**
     * 将角度归一化到 [-PI, PI) 范围内
     */
    private double normalizeAngle(double angle) {
        angle = angle % (2 * Math.PI);
        if (angle >= Math.PI) {
            angle -= 2 * Math.PI;
        }
        if (angle < -Math.PI) {
            angle += 2 * Math.PI;
        }
        return angle;
    }
    //endregion

    //region 内部数据类
    private static class AccelData {
        final long timestamp;
        final float magnitude;
        final float[] values;

        AccelData(long timestamp, float magnitude, float[] values) {
            this.timestamp = timestamp;
            this.magnitude = magnitude;
            this.values = values.clone();
        }
    }
    //endregion
}
