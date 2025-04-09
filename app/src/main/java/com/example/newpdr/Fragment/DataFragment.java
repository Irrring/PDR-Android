package com.example.newpdr.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.newpdr.DataClass.*;
import com.example.newpdr.R;
import com.example.newpdr.ViewModel.SensorViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class DataFragment extends Fragment {
    private SensorViewModel viewModel;
    private LineChart accelChart, gyroChart;
    private MaterialButton btnStart, btnStop, btnSave;

    // 加速度计 TextView
    private TextView tvAccel, tvAccelX, tvAccelY, tvAccelZ;
    // 陀螺仪 TextView
    private TextView tvGyro, tvGyroX, tvGyroY, tvGyroZ;
    // 磁强计 TextView
    private TextView tvMag, tvMagX, tvMagY, tvMagZ;
    // 气压计 TextView
    private TextView tvPressure;

    // 图表配置
    private static final int MAX_DATA_POINTS = 200;
    private static final int[] ACCEL_COLORS = {Color.RED, Color.GREEN, Color.BLUE};
    private static final int[] GYRO_COLORS = {Color.MAGENTA, Color.CYAN, Color.YELLOW};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_data, container, false);
        initViews(root);
        setupCharts();

        // 动态设置按钮图标大小
        MaterialButton btnStart = root.findViewById(R.id.btn_start);
        MaterialButton btnStop = root.findViewById(R.id.btn_stop);
        MaterialButton btnSave = root.findViewById(R.id.btn_save);

        btnStart.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int buttonHeight = btnStart.getHeight();
                int iconSize = (int) (buttonHeight * 0.8f);
                btnStart.setIconSize(iconSize);
                btnStop.setIconSize(iconSize);
                btnSave.setIconSize(iconSize);
                btnStart.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        return root;
    }

    private void initViews(View root) {
        accelChart = root.findViewById(R.id.accel_chart);
        gyroChart = root.findViewById(R.id.gyro_chart);
        btnStart = root.findViewById(R.id.btn_start);
        btnStop = root.findViewById(R.id.btn_stop);
        btnSave = root.findViewById(R.id.btn_save);

        // 初始化加速度计 TextView
        tvAccel = root.findViewById(R.id.tvAccel);
        tvAccelX = root.findViewById(R.id.tvAccelX);
        tvAccelY = root.findViewById(R.id.tvAccelY);
        tvAccelZ = root.findViewById(R.id.tvAccelZ);

        // 初始化陀螺仪 TextView
        tvGyro = root.findViewById(R.id.tvGyro);
        tvGyroX = root.findViewById(R.id.tvGyroX);
        tvGyroY = root.findViewById(R.id.tvGyroY);
        tvGyroZ = root.findViewById(R.id.tvGyroZ);

        // 初始化磁强计 TextView
        tvMag = root.findViewById(R.id.tvMag);
        tvMagX = root.findViewById(R.id.tvMagX);
        tvMagY = root.findViewById(R.id.tvMagY);
        tvMagZ = root.findViewById(R.id.tvMagZ);

        // 初始化气压计 TextView
        tvPressure = root.findViewById(R.id.tvPressure);
    }

    private void setupCharts() {
        configureChart(accelChart, "加速度计数据",
                new String[]{"X轴", "Y轴", "Z轴"}, ACCEL_COLORS);
        configureChart(gyroChart, "陀螺仪数据",
                new String[]{"X轴", "Y轴", "Z轴"}, GYRO_COLORS);
    }

    private void configureChart(LineChart chart, String title,
                                String[] labels, int[] colors) {
        chart.getDescription().setText(title);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);

        LineData data = new LineData(
                createDataSet(labels[0], colors[0]),
                createDataSet(labels[1], colors[1]),
                createDataSet(labels[2], colors[2])
        );
        chart.setData(data);
    }

    private LineDataSet createDataSet(String label, int color) {
        LineDataSet set = new LineDataSet(new ArrayList<>(), label);
        set.setColor(color);
        set.setDrawCircles(false);
        set.setLineWidth(1.5f);
        return set;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SensorViewModel.class);

        setupButtonListeners();
        setupDataObservers();
        loadHistoryData();
    }

    private void setupButtonListeners() {
        btnStart.setOnClickListener(v -> startCollectionWithLocationCheck());
        btnStop.setOnClickListener(v -> viewModel.stopCollection());
        btnSave.setOnClickListener(v -> saveSensorData());
    }

    private void startCollectionWithLocationCheck() {
        Log.d("MagCali", "cali_require " + viewModel.getSettingsManager().isCalibrationRequired());
        viewModel.magnetometerCalibrator.setCalibrated(!viewModel.getSettingsManager().isCalibrationRequired());
        viewModel.startCollection();

        if (!viewModel.magnetometerCalibrator.isCalibrated()) {
            if (viewModel.get_GaoDe_Location().getValue() == null) {
                Toast.makeText(getContext(), "高德定位初始化中，同时进行磁强计校准，请耐心等待...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "定位初始化成功！", Toast.LENGTH_SHORT).show();
            }
            viewModel.isCalibrating.postValue(true);
            CalibrationDialogFragment calibrationDialog = new CalibrationDialogFragment();
            calibrationDialog.setCancelable(false);
            calibrationDialog.show(getParentFragmentManager(), "CalibrationDialog");
        }

        if (viewModel.magnetometerCalibrator.isCalibrated() && viewModel.get_GaoDe_Location().getValue() == null) {
            Toast.makeText(getContext(), "高德定位初始化中，请耐心等待...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "初始化成功，开始解算！", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDataObservers() {
        viewModel.getLiveData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                updateRealtimeDisplay(data);

                switch (data.getType()) {
                    case ACCEL:
                        viewModel.pdrProcessor.processAccelerometer(
                                System.currentTimeMillis(),
                                data.getValues()
                        );
                        viewModel.checkAndRecordPdrPoint();
                        break;
                    case GYRO:
                        viewModel.pdrProcessor.processGyroscope(
                                System.currentTimeMillis(),
                                data.getValues()
                        );
                        break;
                    case MAG:
                        float[] magValues = data.getValues();
                        if (viewModel.magnetometerCalibrator.isCalibrated()) {
                            magValues = viewModel.magnetometerCalibrator.applyCalibration(magValues);
                            viewModel.pdrProcessor.processMagnetometer(magValues);
                        }
                        break;
                    case PRESSURE:
                        break;
                }
            }
        });
    }

    private void loadHistoryData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<AccelData> accelHistory = viewModel.getAccelHistory();
            List<GyroData> gyroHistory = viewModel.getGyroHistory();

            requireActivity().runOnUiThread(() -> {
                synchronized (accelChart.getData()) {
                    updateChartAccelData(accelChart, accelHistory);
                }
                synchronized (gyroChart.getData()) {
                    updateChartGyroData(gyroChart, gyroHistory);
                }
            });
        });
    }

    private void updateRealtimeDisplay(SensorData data) {
        float[] values = data.getValues();
        String timestamp = String.valueOf(data.getTimestamp() % 100000); // 简化时间戳显示

        switch (data.getType()) {
            case ACCEL:
                tvAccel.setText("加速度计");
                tvAccelX.setText("X: " + String.format(Locale.US, "%.3f", values[0]));
                tvAccelY.setText("Y: " + String.format(Locale.US, "%.3f", values[1]));
                tvAccelZ.setText("Z: " + String.format(Locale.US, "%.3f", values[2]));
                break;
            case GYRO:
                tvGyro.setText("陀螺仪");
                tvGyroX.setText("X: " + String.format(Locale.US, "%.3f", values[0]));
                tvGyroY.setText("Y: " + String.format(Locale.US, "%.3f", values[1]));
                tvGyroZ.setText("Z: " + String.format(Locale.US, "%.3f", values[2]));
                break;
            case MAG:
                tvMag.setText("磁强计");
                tvMagX.setText("X: " + String.format(Locale.US, "%.3f", values[0]));
                tvMagY.setText("Y: " + String.format(Locale.US, "%.3f", values[1]));
                tvMagZ.setText("Z: " + String.format(Locale.US, "%.3f", values[2]));
                break;
            case PRESSURE:
                tvPressure.setText("气压  " + String.format(Locale.US, "%.2f", values[0]));
                break;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            if (data.getType() == SensorData.SensorType.ACCEL) {
                addChartEntry(accelChart, data, ACCEL_COLORS);
            } else if (data.getType() == SensorData.SensorType.GYRO) {
                addChartEntry(gyroChart, data, GYRO_COLORS);
            }
        });
    }

    private void updateChartAccelData(LineChart chart, List<AccelData> history) {
        LineData data = chart.getData();
        if (data == null) return;

        for (int i = 0; i < 3; i++) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(i);
            List<Entry> entries = new ArrayList<>();

            for (AccelData d : history) {
                float value = d.getValues()[i];
                long time = d.getTimestamp() % 100000;
                entries.add(new Entry(time, value));
            }

            set.setValues(entries.subList(Math.max(0, entries.size() - MAX_DATA_POINTS), entries.size()));
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private void updateChartGyroData(LineChart chart, List<GyroData> history) {
        LineData data = chart.getData();
        if (data == null) return;

        for (int i = 0; i < 3; i++) {
            LineDataSet set = (LineDataSet) data.getDataSetByIndex(i);
            List<Entry> entries = new ArrayList<>();

            for (GyroData d : history) {
                float value = d.getValues()[i];
                long time = d.getTimestamp() % 100000;
                entries.add(new Entry(time, value));
            }

            set.setValues(entries.subList(Math.max(0, entries.size() - MAX_DATA_POINTS), entries.size()));
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private void addChartEntry(LineChart chart, SensorData data, int[] colors) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            LineData lineData = chart.getData();
            if (lineData == null) return;

            long time = data.getTimestamp() % 100000;
            float[] values = data.getValues();

            synchronized (lineData) {
                for (int i = 0; i < 3; i++) {
                    LineDataSet set = (LineDataSet) lineData.getDataSetByIndex(i);
                    List<Entry> entries = new ArrayList<>(set.getValues());
                    if (entries.size() > MAX_DATA_POINTS) {
                        entries.remove(0);
                    }
                    entries.add(new Entry(time, values[i]));
                    set.setValues(entries);
                }
            }

            lineData.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.moveViewToX(lineData.getEntryCount());
        });
    }

    private void saveSensorData() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                String fileName = "sensor_data_" + sdf.format(new Date()) + ".csv";

                try (FileWriter writer = new FileWriter(new File(dir, fileName))) {
                    writeAccelData(writer);
                    writeGyroData(writer);
                    showToast("数据保存成功: " + fileName);
                }
            } catch (IOException e) {
                showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private void writeAccelData(FileWriter writer) throws IOException {
        writer.write("加速度计数据\n");
        writer.write("时间戳,X,Y,Z\n");
        for (AccelData data : viewModel.getAccelHistory()) {
            writer.write(String.format(Locale.US, "%d,%.4f,%.4f,%.4f\n",
                    data.getTimestamp(),
                    data.getX(),
                    data.getY(),
                    data.getZ()));
        }
    }

    private void writeGyroData(FileWriter writer) throws IOException {
        writer.write("\n陀螺仪数据\n");
        writer.write("时间戳,X,Y,Z\n");
        for (GyroData data : viewModel.getGyroHistory()) {
            writer.write(String.format(Locale.US, "%d,%.6f,%.6f,%.6f\n",
                    data.getTimestamp(),
                    data.getX(),
                    data.getY(),
                    data.getZ()));
        }
    }

    private void showToast(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show());
    }
}