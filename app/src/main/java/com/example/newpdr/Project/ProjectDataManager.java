package com.example.newpdr.Project;

import android.content.Context;
import android.util.Log;

import com.amap.api.maps.model.LatLng;
import com.example.newpdr.ViewModel.SensorViewModel;
import com.example.newpdr.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import com.example.newpdr.DataClass.*;
import com.example.newpdr.utils.SettingsManager;

public class ProjectDataManager {
    private static final String TAG = "ProjectDataManager";
    private File projectDir;

    // 构造函数，初始化项目目录
    public ProjectDataManager(File projectDir) {
        this.projectDir = projectDir;
    }

    // 统一保存入口,包括各种传感器和地图
    public void saveAllSensorData(SensorViewModel sensorModel) {
        saveSensorData(sensorModel.getAccelHistory(),sensorModel.getGyroHistory(),sensorModel.getMagHistory(),sensorModel.getPressureHistory());
        saveGaoDeHistory(sensorModel.getGaoDe_History());
        savePDRData(sensorModel.getPDRHistory());
    }

    // 保存项目元数据到 meta.json
    public void saveProjectMeta(Project project) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("projectId", project.getProjectId());
            meta.put("projectName", project.getProjectName());
            meta.put("createTime", project.getCreateTime().getTime());
            meta.put("lastModified", new Date().getTime());

            File metaFile = new File(projectDir, "meta.json");
            FileUtils.writeToFile(metaFile, meta.toString());
            Log.d(TAG, "Project meta saved successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Save meta failed", e);
        }
    }

    // 统一保存传感器数据的私有方法
    void saveSensorData(List<AccelData> accelDataList, List<GyroData> gyroDataList,
                        List<MagData> magDataList, List<PressureData> pressureDataList) {
        try {
            // 创建文件夹
            File sensorDir = new File(projectDir, "sensor_data");
            if (!sensorDir.exists()) sensorDir.mkdirs();

            // 保存加速度数据
            if (accelDataList != null && !accelDataList.isEmpty()) {
                JSONArray accelArray = new JSONArray();
                for (AccelData data : accelDataList) {
                    accelArray.put(data.toJSON());
                }
                File accelFile = new File(sensorDir, "accel.json");
                FileUtils.writeToFile(accelFile, accelArray.toString());
            }

            // 保存陀螺仪数据
            if (gyroDataList != null && !gyroDataList.isEmpty()) {
                JSONArray gyroArray = new JSONArray();
                for (GyroData data : gyroDataList) {
                    gyroArray.put(data.toJSON());
                }
                File gyroFile = new File(sensorDir, "gyro.json");
                FileUtils.writeToFile(gyroFile, gyroArray.toString());
            }

            // 保存磁力计数据
            if (magDataList != null && !magDataList.isEmpty()) {
                JSONArray magArray = new JSONArray();
                for (MagData data : magDataList) {
                    magArray.put(data.toJSON());
                }
                File magFile = new File(sensorDir, "mag.json");
                FileUtils.writeToFile(magFile, magArray.toString());
            }

            // 保存气压数据
            if (pressureDataList != null && !pressureDataList.isEmpty()) {
                JSONArray pressureArray = new JSONArray();
                for (PressureData data : pressureDataList) {
                    pressureArray.put(data.toJSON());
                }
                File pressureFile = new File(sensorDir, "pressure.json");
                FileUtils.writeToFile(pressureFile, pressureArray.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "Save sensor data failed", e);
        }
    }



    // 保存高德地图轨迹到 map_data 目录
    public void saveGaoDeHistory(List<LatLng> history) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (LatLng point : history) {
                JSONObject obj = new JSONObject();
                obj.put("lat", point.latitude);
                obj.put("lng", point.longitude);
                jsonArray.put(obj);
            }

            File mapDir = new File(projectDir, "map_data");
            if (!mapDir.exists()) mapDir.mkdirs();

            FileUtils.writeToFile(new File(mapDir, "gaode_track.json"), jsonArray.toString());
        } catch (Exception e) {
            Log.e(TAG, "Save GaoDe track failed", e);
        }
    }

    // 保存PDR数据
    public void savePDRData(List<PDRPoint> pdrHistory) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (PDRPoint point : pdrHistory) {
                JSONObject obj = new JSONObject();
                obj.put("x", point.x);
                obj.put("y", point.y);
                jsonArray.put(obj);
            }

            File mapDir = new File(projectDir, "map_data");
            if (!mapDir.exists()) mapDir.mkdirs();

            FileUtils.writeToFile(new File(mapDir, "pdr_point.json"), jsonArray.toString());
        } catch (Exception e) {
            Log.e(TAG, "Save PDR Point failed", e);
        }
    }

    // 新增配置保存方法
    public void saveConfigData(SettingsManager settings) {
        try {
            // 创建配置目录
            File configDir = new File(projectDir, "config");
            if (!configDir.exists() && !configDir.mkdirs()) {
                Log.e(TAG, "Failed to create config directory");
                return;
            }

            // 构建 JSON 配置对象
            JSONObject config = new JSONObject();
            config.put("calibrationRequired", settings.isCalibrationRequired());
            config.put("stepModel", settings.getStepModel());
            config.put("stepLength", settings.getStepLength());
            config.put("height", settings.getHeight());
            config.put("stepWindow", settings.getStepWindow());
            config.put("magneticDeclination", settings.getMagneticDeclination());
            config.put("floorDetection", settings.isFloorDetectionEnabled());
            config.put("initialFloor", settings.getInitialFloor());
            config.put("floorHeight", settings.getFloorHeight());

            // 写入配置文件
            File configFile = new File(configDir, "config.json");
            FileUtils.writeStringToFile(configFile, config.toString(2)); // 带缩进的JSON
            Log.i(TAG, "Configuration saved to: " + configFile.getAbsolutePath());
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Save config failed: " + e.getMessage(), e);
        }
    }

    // 加载项目数据（如 meta.json）
    public JSONObject loadProjectMeta() {
        try {
            File metaFile = new File(projectDir, "meta.json");
            if (metaFile.exists()) {
                String json = FileUtils.readFromFile(metaFile);
                return new JSONObject(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load project meta failed", e);
        }
        return null;
    }

    // 加载传感器数据
    public String loadSensorData() {
        try {
            File sensorDataFile = new File(projectDir, "sensor_data/data.txt");
            if (sensorDataFile.exists()) {
                return FileUtils.readFromFile(sensorDataFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load sensor data failed", e);
        }
        return null;
    }

    // 加载地图数据
    public String loadMapData() {
        try {
            File mapDataFile = new File(projectDir, "map_data/map.json");
            if (mapDataFile.exists()) {
                return FileUtils.readFromFile(mapDataFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load map data failed", e);
        }
        return null;
    }

    // 加载配置数据
    public String loadConfigData() {
        try {
            File configFile = new File(projectDir, "config/config.json");
            if (configFile.exists()) {
                return FileUtils.readFromFile(configFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Load config data failed", e);
        }
        return null;
    }
}
