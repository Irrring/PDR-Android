package com.example.newpdr.ViewModel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.amap.api.maps.model.LatLng;
import com.example.newpdr.DataClass.*;
import com.example.newpdr.utils.PDRProcessor;
import com.example.newpdr.DataClass.PDRPoint;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SensorViewModel extends ViewModel {
    // 实时数据流（统一格式）
    private final MutableLiveData<SensorData> liveData = new MutableLiveData<>();


    // 新增位置更新LiveData
    private final MutableLiveData<PDRPoint> latestPdrPoint = new MutableLiveData<>();




    // 分类历史存储（线程安全）
    private final LinkedList<AccelData> accelHistory = new LinkedList<>();
    private final LinkedList<GyroData> gyroHistory = new LinkedList<>();
    private final LinkedList<MagData> magHistory = new LinkedList<>();
    private final LinkedList<PressureData> pressureHistory = new LinkedList<>();

    // 同步锁对象（每个传感器独立锁）
    private final Object accelLock = new Object();
    private final Object gyroLock = new Object();
    private final Object magLock = new Object();
    private final Object pressureLock = new Object();

    // 控制状态
    private final MutableLiveData<Boolean> isCollecting = new MutableLiveData<>(false);


    // 新增PDR相关数据
    private final MutableLiveData<Double> currentYaw = new MutableLiveData<>(0.0);
    private final MutableLiveData<double[]> currentPosition = new MutableLiveData<>(new double[2]);
    private final LinkedList<PDRPoint> pdrHistory = new LinkedList<>();


    // PDR 数据处理类
    public  PDRProcessor pdrProcessor = new PDRProcessor();


    // 高德地图定位API数据保存接口（持续更新定位结果）
    private final MutableLiveData<LatLng> GaoDe_Location = new MutableLiveData<>();


    // BLH -- ENU 的圆心（局部坐标系原点），初始为 null
    private LatLng BLH_Origin = null;


    // 用于存储高德定位轨迹点，便于后续统一保存
    private final LinkedList<LatLng> GaoDe_History = new LinkedList<>();



    /**
     * 添加传感器数据（入口方法）
     * data 统一格式的传感器数据
     */

    private int lastRecordedStepCount = 0;

    public void addSensorData(SensorData data) {
        // 更新实时数据流
        liveData.postValue(data);

        // 根据类型更新对应历史存储
        switch (data.getType()) {
            case ACCEL:
                addAccelData(new AccelData(
                        data.getTimestamp(),
                        data.getValues()[0],
                        data.getValues()[1],
                        data.getValues()[2]
                ));
                break;

            case GYRO:
                addGyroData(new GyroData(
                        data.getTimestamp(),
                        data.getValues()[0],
                        data.getValues()[1],
                        data.getValues()[2]
                ));
                break;

            case MAG:
                addMagData(new MagData(
                        data.getTimestamp(),
                        data.getValues()[0],
                        data.getValues()[1],
                        data.getValues()[2]
                ));
                break;

            case PRESSURE:
                addPressureData(new PressureData(
                        data.getTimestamp(),
                        data.getValues()[0]
                ));
                break;


        }
    }


    public synchronized void checkAndRecordPdrPoint() {

        int currentStepCount = pdrProcessor.getStepCount();

        Log.d("AddPoint","checking" + currentStepCount  );

        if (currentStepCount > lastRecordedStepCount) {
            // 计算新增步数
            int newSteps = currentStepCount - lastRecordedStepCount;


                // 创建轨迹点
                double[] pos = pdrProcessor.getCurrentPosition();
                PDRPoint point = new PDRPoint();
                point.x = pos[1]; // 东向位移
                point.y = pos[0]; // 北向位移
                point.timestamp = System.currentTimeMillis();
                point.accuracy = calculateStepAccuracy(); // 精度估算（需实现）

                // 线程安全添加
                // 添加调试日志
                Log.d("AddPoint",
                        " 最新点坐标:(" + point.x + "," + point.y + ")");

                synchronized (pdrHistory) {
                    pdrHistory.add(point);
                }


                // 添加调试日志
                Log.d("AddPoint", "新增步数:" + newSteps +
                        " 当前历史记录数:" + pdrHistory.size() +
                        " 最新点坐标:(" + point.x + "," + point.y + ")");

                // 发送最新点通知
                latestPdrPoint.postValue(point); // 新增此行

            lastRecordedStepCount = currentStepCount;
        }
    }

//    // 精度估算示例方法
    private double calculateStepAccuracy() {
        return 0.5; // 根据传感器噪声动态计算
    }

    // region 各传感器数据存储方法
    private void addAccelData(AccelData data) {
        synchronized (accelLock) {
            accelHistory.add(data);
            trimHistory(accelHistory, 1000);
        }
    }

    private void addGyroData(GyroData data) {
        synchronized (gyroLock) {
            gyroHistory.add(data);
            trimHistory(gyroHistory, 1000);
        }
    }

    private void addMagData(MagData data) {
        synchronized (magLock) {
            magHistory.add(data);
            trimHistory(magHistory, 1000);
        }
    }

    private void addPressureData(PressureData data) {
        synchronized (pressureLock) {
            pressureHistory.add(data);
            trimHistory(pressureHistory, 1000);
        }
    }
    // endregion

    // region 公共访问接口
    public LiveData<PDRPoint> getLatestPdrPoint() {
        return latestPdrPoint;
    }

    public LiveData<SensorData> getLiveData() {
        return liveData;
    }

    public List<AccelData> getAccelHistory() {
        synchronized (accelLock) {
            return Collections.unmodifiableList(new LinkedList<>(accelHistory));
        }
    }

    public List<GyroData> getGyroHistory() {
        synchronized (gyroLock) {
            return Collections.unmodifiableList(new LinkedList<>(gyroHistory));
        }
    }

    public List<MagData> getMagHistory() {
        synchronized (magLock) {
            return Collections.unmodifiableList(new LinkedList<>(magHistory));
        }
    }

    public List<PressureData> getPressureHistory() {
        synchronized (pressureLock) {
            return Collections.unmodifiableList(new LinkedList<>(pressureHistory));
        }
    }


    public PDRProcessor getPdrProcessor() {
        return pdrProcessor;
    }


    public LiveData<LatLng> get_GaoDe_Location() {
        return GaoDe_Location;
    }

    public void set_GaoDe_Location(LatLng location) {
        GaoDe_Location.postValue(location);
    }

    public void addGaoDe_History(LatLng location) {
            GaoDe_History.add(location);
            trimHistory(GaoDe_History, 1000);
    }

    public  List<LatLng> getGaoDe_History(){
        return Collections.unmodifiableList(new LinkedList<>(GaoDe_History));
    }


    public LatLng get_BLH_Origin() {
        return BLH_Origin;
    }

    public void set_BLH_Origin(LatLng location) {
        BLH_Origin = location;
    }
    // endregion




    // region 控制方法
    public LiveData<Boolean> isCollecting() {
        return isCollecting;
    }

    public void startCollection() {
        isCollecting.postValue(true);
    }

    public void stopCollection() {
        isCollecting.postValue(false);
    }
    // endregion



    // region 辅助方法
    private <T> void trimHistory(LinkedList<T> history, int maxSize) {
        while (history.size() > maxSize) {
            history.removeFirst();
        }
    }
    // endregion



    @Override
    protected void onCleared() {
        super.onCleared();
        clearAllHistory();
    }

    private void clearAllHistory() {
        synchronized (accelLock) { accelHistory.clear(); }
        synchronized (gyroLock) { gyroHistory.clear(); }
        synchronized (magLock) { magHistory.clear(); }
        synchronized (pressureLock) { pressureHistory.clear(); }
    }


}