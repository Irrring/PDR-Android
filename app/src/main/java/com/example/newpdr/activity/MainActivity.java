package com.example.newpdr.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.model.LatLng;
import com.example.newpdr.DataClass.SensorData;
import com.example.newpdr.Fragment.DataFragment;
import com.example.newpdr.Fragment.MapFragment;
import com.example.newpdr.Fragment.SettingsFragment;
import com.example.newpdr.R;
import com.example.newpdr.ViewModel.SensorViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private SensorManager sensorManager;
    private SensorViewModel viewModel;

    // 传感器实例
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private Sensor barometer;


    // 定位相关
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;
    private static final int LOCATION_PERMISSION_CODE = 1001;


    // 采用 Fragment 保留状态的方式，提前创建好三个 Fragment
    private DataFragment dataFragment;
    private MapFragment mapFragment;
    private SettingsFragment settingsFragment;




    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 强制应用使用白天模式
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // 初始化核心组件
        viewModel = new ViewModelProvider(this).get(SensorViewModel.class);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // 初始化传感器
        initializeSensors();

        // 初始化 Fragment（采用 add()/hide()/show() 方式保留状态）
        setupFragments(savedInstanceState);

        // 初始化导航
        setupNavigation();

        // 初始化传感器状态观察者
        setupSensorStateObserver();


        // 初始化定位服务
        initLocationService();
    }

    // 如果是第一次启动，则创建并 add 所有 Fragment；如果不是则从 FragmentManager 中查找
    private void setupFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            dataFragment = new DataFragment();
            mapFragment = new MapFragment();
            settingsFragment = new SettingsFragment();

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, dataFragment, "dataFragment")
                    .add(R.id.fragment_container, mapFragment, "mapFragment")
                    .hide(mapFragment)
                    .add(R.id.fragment_container, settingsFragment, "settingsFragment")
                    .hide(settingsFragment)
                    .commit();
        } else {
            dataFragment = (DataFragment) getSupportFragmentManager().findFragmentByTag("dataFragment");
            mapFragment = (MapFragment) getSupportFragmentManager().findFragmentByTag("mapFragment");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settingsFragment");
        }
    }

    // 设置底部导航栏，选择时只需 show 对应的 Fragment 并隐藏其他 Fragment
    private void setupNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showFragment(dataFragment);
                return true;
            } else if (itemId == R.id.nav_map) {
                if (hasLocationPermission()) {
                    showFragment(mapFragment);
                    return true;
                } else {
                    requestLocationPermission();
                    return false;
                }
            } else if (itemId == R.id.nav_settings) {
                showFragment(settingsFragment);
                return true;
            }
            return false;
        });
    }

    // 显示指定 Fragment，隐藏其他所有 Fragment
    private void showFragment(Fragment fragmentToShow) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (dataFragment != null) transaction.hide(dataFragment);
        if (mapFragment != null) transaction.hide(mapFragment);
        if (settingsFragment != null) transaction.hide(settingsFragment);
        transaction.show(fragmentToShow);
        transaction.commit();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFragment(mapFragment);
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要位置权限")
                .setMessage("地图功能需要访问您的位置信息")
                .setPositiveButton("设置", (dialog, which) -> openAppSettings())
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }



    // TODO: 高德API接入
    private void initLocationService() {
        try
        {
            // 隐私合规要求（部分 SDK 版本需要调用此方法）
            com.amap.api.location.AMapLocationClient.updatePrivacyShow(getApplicationContext(), true, true);
            com.amap.api.location.AMapLocationClient.updatePrivacyAgree(getApplicationContext(), true);


            locationClient = new AMapLocationClient(getApplicationContext());
            locationOption = new AMapLocationClientOption();
            locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            locationOption.setInterval(1000);  // 定位间隔（毫秒），可根据需要调整
            locationOption.setSensorEnable(true);
            locationOption.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.Sport);
            locationClient.setLocationOption(locationOption);

            // 定位回调：持续接收定位更新，并更新 Gaode 轨迹
            locationClient.setLocationListener(aMapLocation ->
            {
                if (aMapLocation != null && aMapLocation.getErrorCode() == 0)
                {
                    runOnUiThread(() -> handleLocationUpdate(aMapLocation));
                }
                else if (aMapLocation != null)
                {
                    int errorCode = aMapLocation.getErrorCode();
                    String errorInfo = aMapLocation.getErrorInfo();
                    Log.e("AMapLocation", "定位失败，错误码: " + errorCode + " 错误信息: " + errorInfo);
                    Toast.makeText(this,"定位失败", Toast.LENGTH_SHORT).show();
                }
            });

            locationClient.startLocation();

        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void handleLocationUpdate(AMapLocation location)
    {
        LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        // 如果还没有设置 BLH_Origin，则设置为当前定位
        if (viewModel.get_BLH_Origin() == null) {
            viewModel.set_BLH_Origin(currentLocation);
        }
        // 持续更新当前定位，并加入轨迹
        viewModel.set_GaoDe_Location(currentLocation);
        viewModel.addGaoDe_History(currentLocation);
    }



    // 传感器状态观察
    private void setupSensorStateObserver() {
        viewModel.isCollecting().observe(this, isCollecting -> {
            if (Boolean.TRUE.equals(isCollecting)) {
                registerSensors();
            } else {
                unregisterSensors();
            }
        });
    }

    private void initializeSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    private void registerSensors() {
        try {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
            if (magnetometer != null) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (barometer != null) {
                sensorManager.registerListener(this, barometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "安全异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Boolean isCollecting = viewModel.isCollecting().getValue();
        if (isCollecting == null || !isCollecting) {
            return;
        }
        SensorData data = convertSensorEvent(event);
        if (data != null) {
            viewModel.addSensorData(data);
        }
    }

    private SensorData convertSensorEvent(SensorEvent event) {
        long timestamp = System.currentTimeMillis();
        float[] values = event.values.clone();
        SensorData.SensorType type = null;

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                type = SensorData.SensorType.ACCEL;
                values = convertRFUtoFRD(values); // 添加坐标转换
                break;
            case Sensor.TYPE_GYROSCOPE:
                type = SensorData.SensorType.GYRO;
                values = convertRFUtoFRD(values); // 陀螺仪同样需要转换
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                type = SensorData.SensorType.MAG;
                values = convertRFUtoFRD(values); // 磁力计转换
                break;
            case Sensor.TYPE_PRESSURE:
                type = SensorData.SensorType.PRESSURE;
                break;
            default:
                return null;
        }
        return new SensorData(timestamp, type, values);
    }

    // 新增坐标转换方法
    private float[] convertRFUtoFRD(float[] original) {
        return new float[] {
                original[1],  // X' = 原始Y（前）
                original[0],  // Y' = 原始X（右）
                -original[2]  // Z' = -原始Z（下）
        };
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 可根据需要处理精度变化
    }

    @Override
    protected void onPause() {
        super.onPause();
        Boolean isCollecting = viewModel.isCollecting().getValue();
        if (Boolean.TRUE.equals(isCollecting)) {
            viewModel.stopCollection();
        }
    }
}
