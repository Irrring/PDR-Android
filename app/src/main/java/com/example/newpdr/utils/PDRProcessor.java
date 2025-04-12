package com.example.newpdr.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

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

    // 过程噪声（针对航向角误差的1维状态）
    private static final double Q_heading = 1e-4;
    // 磁力计观测噪声
    private static final double R = 0.05;
    //endregion

    //region 状态变量
    private final LinkedList<AccelData> accelWindow = new LinkedList<>();
    private long lastStepTime = 0;

    private long currentTime=0;
    //region 新增步间隔记录
    private long lastStepInterval = 0;  // 上一次脚步时间间隔（毫秒）
    private long prevStepInterval = 0;  // 上上次脚步时间间隔（毫秒）

    private double steplength = 0;
    //endregion


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
    private SettingsManager settingsManager;

    //region 常量配置
    private static final long MIN_STEP_INTERVAL = 300;     // 最小步间间隔(ms)
    private static final float STEP_THRESHOLD = 10.8f;     // 脚步检测阈值(m/s²)

    // 配置信息获取
    private int ACC_WINDOW_SIZE = 20;       // 加速度滑动窗口大小 20

    private  double BASE_STEP_LENGTH = 0.7;    // 基础步长(m) 0.7
    private String stepModel = "constant";
    private float height = 1.75f;
    private float MAGNETIC_DECLINATION = -3.3f; // 地磁偏角


    // region 高程探测相关变量
    private boolean useAltitudeDetection = true; // 是否使用高程探测
    private double initPressure;  // 基准气压（hPa）
    private int initialFloor = 1;     // 初始楼层
    private int currentFloor = 1;      // 当前楼层
    private double FLOOR_HEIGHT = 3.0; // 每层高度3米
    private static final int MIN_FLOOR = 1; // 最小楼层
    private LinkedList<Double> pressureWindow = new LinkedList<>();
    private static final int PRESSURE_WINDOW_SIZE = 10; // 气压数据滑动窗口大小
    // endregion

    // 通过构造函数注入 SettingsManager
    public PDRProcessor(SettingsManager settingsManager) {
        if (settingsManager == null) {
            Log.e("PDRProcessor", "SettingsManager is null");
            throw new IllegalArgumentException("SettingsManager must not be null");
        }
        this.settingsManager = settingsManager;
        // 获取配置
        ACC_WINDOW_SIZE = settingsManager.getStepWindow();
        BASE_STEP_LENGTH = settingsManager.getStepLength();
        stepModel = settingsManager.getStepModel();
        height = settingsManager.getHeight();
        MAGNETIC_DECLINATION = (float) Math.toRadians(settingsManager.getMagneticDeclination());



        //TODO:建议配置添加高程探测相关内容
        // 添加高程探测配置
         useAltitudeDetection = settingsManager.isFloorDetectionEnabled();
         initialFloor = settingsManager.getInitialFloor();
         FLOOR_HEIGHT=settingsManager.getFloorHeight();
    }

    //region 公开接口

    public void UpdateSettings(SettingsManager settingsManager)
    {
        if (settingsManager == null) {
            Log.e("PDRProcessor", "SettingsManager is null");
            throw new IllegalArgumentException("SettingsManager must not be null");
        }
        this.settingsManager = settingsManager;
        // 获取配置
        ACC_WINDOW_SIZE = settingsManager.getStepWindow();
        BASE_STEP_LENGTH = settingsManager.getStepLength();
        stepModel = settingsManager.getStepModel();
        height = settingsManager.getHeight();
        MAGNETIC_DECLINATION = (float) Math.toRadians(settingsManager.getMagneticDeclination());

        
        // 添加高程探测配置
        useAltitudeDetection = settingsManager.isFloorDetectionEnabled();
        initialFloor = settingsManager.getInitialFloor();
        FLOOR_HEIGHT=settingsManager.getFloorHeight();
    }



    // 添加气压数据处理方法
    public void processBarometer(double pressure) {
        if (!useAltitudeDetection) return;

        // 平滑滤波处理
        pressureWindow.add(pressure);
        if (pressureWindow.size() > PRESSURE_WINDOW_SIZE) {
            pressureWindow.removeFirst();
        }
        double smoothedPressure = pressureWindow.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(pressure);

        // 初始化基准气压
        if (initPressure == 0) {
            initPressure = smoothedPressure;
            currentFloor = initialFloor;
            Log.d("Pressure", "初始化基准气压: " + initPressure + " hPa, 初始楼层: " + currentFloor);
            return;
        }


        double h_refer=44330.0*(1.0-Math.pow(initPressure*0.1/ 101.325, 1.0/5.255));

        //Log.d("Pressure", "initPressure: "+initPressure);
        // 计算海拔高度差
        double h_current = calculateAltitude(smoothedPressure);

        //Log.d("Pressure", "h_current "+h_current);
        Log.d("Pressure", "dH  "+(h_current-h_refer));

        // 计算楼层变化
        int floorChange = (int)((h_current-h_refer) / FLOOR_HEIGHT);

        currentFloor = Math.max(MIN_FLOOR, initialFloor + floorChange);
        Log.d("Pressure", "当前楼层=" + currentFloor + "，海拔变化=" + (h_current-h_refer) + "m");

    }

    // 气压转海拔计算（使用提供的公式）
    private double calculateAltitude(double currentPressure) {
        return 44330.0 * (1.0 - Math.pow(currentPressure*0.1 / 101.325, 1.0/5.255));
    }

    // 添加获取当前楼层的方法
    public int getCurrentFloor() {
        return useAltitudeDetection ? currentFloor : initialFloor;
    }

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

            // 新增：计算时间间隔
             currentTime = accelWindow.get(targetIndex).timestamp;
            if (lastStepTime != 0) {
                prevStepInterval = lastStepInterval;  // 保存旧间隔
                lastStepInterval = currentTime - lastStepTime; // 计算新间隔
            }

            updatePosition();
            stepCount++;
            lastStepTime = currentTime; // 更新最后一步时间
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
        double stepLength = estimateStepLength(stepModel);

        Log.d("PDRProcessor", "Step Length: " + stepLength);
        position[0] += stepLength * Math.cos(filteredYaw); // 北向(Y)
        position[1] += stepLength * Math.sin(filteredYaw); // 东方(X)

        Log.d("yaw","yaw: " + Math.toDegrees(filteredYaw) );
        Log.d("PDRProcessor","X: " + position[1] + " Y: " + position[0]);

    }

    // TODO:通过步长模型计算步长（常值or身高模型）
    private double estimateStepLength(String stepModel) {
        if ("constant".equals(stepModel)) {
            // 如果是常值模型，返回固定的步长
            return BASE_STEP_LENGTH;
        } else if ("height".equals(stepModel)) {
            // 无有效间隔数据时回退到默认值
            if (lastStepInterval <= 0) return BASE_STEP_LENGTH;

            Log.d("PDRProcessor","X: " + position[1] + " Y: " + position[0]);
            // 处理首次间隔不足的情况（用当前间隔补足）
            long prevInterval = prevStepInterval > 0 ? prevStepInterval : lastStepInterval;

            // 计算加权平均时间间隔（转换为秒）
            double interval1 = lastStepInterval / 1000.0;
            double interval2 = prevInterval / 1000.0;
            Log.d("interval","interval1: " + interval1 + " interval2:" + interval2);
            double avgInterval = 0.8 * interval1 + 0.2 * interval2;

            // 计算步频 S_f（步/秒）
            double S_f = 1.0 / avgInterval;

            Log.d("sf","sf: " + S_f );
            Log.d("height","height: " + height );
            steplength=0.7 + 0.371 * (height - 1.6) +
                    0.227 * (S_f - 1.79) * (height / 1.6);


            Log.d("steplength","steplengtn: " + steplength );
            // 计算步长公式
            return steplength;
        } else {
            // 默认返回常值步长，防止出现非法值
            return BASE_STEP_LENGTH;
        }
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
