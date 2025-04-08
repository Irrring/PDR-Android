package com.example.newpdr.utils;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public class SettingsManager {

    private static final String PREFS_NAME = "PDR_Settings";
    private SharedPreferences preferences;

    // 构造函数，初始化 SharedPreferences
    public SettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // 获取传感器校准状态
    public boolean isCalibrationRequired() {
        return preferences.getBoolean("calibrationRequired", false);
    }

    // 获取步长模型
    public String getStepModel() {
        return preferences.getString("stepModel", "constant");
    }

    // 获取步长值
    public float getStepLength() {
        return preferences.getFloat("stepLength", 0.75f);
    }

    // 获取身高值
    public float getHeight() {
        return preferences.getFloat("height", 1.75f);
    }

    // 获取步长探测窗口
    public int getStepWindow() {
        return preferences.getInt("stepWindow", 20);
    }

    // 获取磁偏角
    public float getMagneticDeclination() {
        return preferences.getFloat("magneticDeclination", 0.0f);
    }

    // 获取楼层探测状态
    public boolean isFloorDetectionEnabled() {
        return preferences.getBoolean("floorDetection", false);
    }

    // 获取初始楼层
    public int getInitialFloor() {
        return preferences.getInt("initialFloor", 1);
    }

    // 获取单层高度
    public float getFloorHeight() {
        return preferences.getFloat("floorHeight", 3.0f);
    }
}
