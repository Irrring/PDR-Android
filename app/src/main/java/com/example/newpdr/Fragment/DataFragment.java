package com.example.newpdr.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataFragment extends Fragment {
    private SensorViewModel viewModel;
    private LineChart accelChart, gyroChart;
    private Button btnStart, btnStop, btnSave;
    private TextView tvAccel, tvGyro, tvMag, tvPressure;

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
        return root;
    }

    private void initViews(View root) {
        accelChart = root.findViewById(R.id.accel_chart);
        gyroChart = root.findViewById(R.id.gyro_chart);
        btnStart = root.findViewById(R.id.btn_start);
        btnStop = root.findViewById(R.id.btn_stop);
        btnSave = root.findViewById(R.id.btn_save);

        tvAccel = root.findViewById(R.id.tvAccel);
        tvGyro = root.findViewById(R.id.tvGyro);
        tvMag = root.findViewById(R.id.tvMag);
        tvPressure = root.findViewById(R.id.tvPressure);
    }


    private void setupCharts() {
        // 配置加速度图表
        configureChart(accelChart, "加速度计数据",
                new String[]{"X轴", "Y轴", "Z轴"}, ACCEL_COLORS);

        // 配置陀螺仪图表
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
        if (viewModel.get_GaoDe_Location().getValue() != null) {
            if (viewModel.get_BLH_Origin() == null) {
                viewModel.set_BLH_Origin(viewModel.get_GaoDe_Location().getValue());
                Toast.makeText(getContext(), "定位初始化成功！", Toast.LENGTH_SHORT).show();
            }
            viewModel.startCollection();
        } else {
            Toast.makeText(getContext(), "等待高德定位初始化，请稍后...", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDataObservers() {
        viewModel.getLiveData().observe(getViewLifecycleOwner(), data -> {
            if (data != null) {
                updateRealtimeDisplay(data);

                // 新增PDR处理
                switch (data.getType()) {
                    case ACCEL:
                        viewModel.pdrProcessor.processAccelerometer(
                                System.currentTimeMillis(),
                                data.getValues()

                        );

                        // 新增：检查并记录PDR点
                        viewModel.checkAndRecordPdrPoint();
                        break;

                    case GYRO:
                        viewModel.pdrProcessor.processGyroscope(
                                System.currentTimeMillis(),
                                data.getValues()
                        );
                        break;
                    case MAG:
                        viewModel.pdrProcessor.processMagnetometer(data.getValues());
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
        // 更新实时文本显示（原有逻辑）
        if(data.getType() == SensorData.SensorType.ACCEL)
        {
            tvAccel.setText(data.toString());
        }
        else if (data.getType() == SensorData.SensorType.GYRO)
        {
            tvGyro.setText(data.toString());
        }
        else if (data.getType() == SensorData.SensorType.MAG)
        {
            tvMag.setText(data.toString());
        }
        else if (data.getType() == SensorData.SensorType.PRESSURE)
        {
            tvPressure.setText(data.toString());
        }



        // 新增图表实时更新
        Executors.newSingleThreadExecutor().execute(() -> {
            if (data.getType() == SensorData.SensorType.ACCEL) {
                addChartEntry(accelChart, data, ACCEL_COLORS);
            } else if (data.getType() == SensorData.SensorType.GYRO) {
                addChartEntry(gyroChart, data, GYRO_COLORS);
            }
        });
    }

    private void updateChartAccelData(LineChart chart,  List<AccelData>  history) {
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

            // 批量更新数据集
            set.setValues(entries.subList(Math.max(0, entries.size() - MAX_DATA_POINTS), entries.size()));
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private void updateChartGyroData(LineChart chart,  List<GyroData>  history) {
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

            // 批量更新数据集
            set.setValues(entries.subList(Math.max(0, entries.size() - MAX_DATA_POINTS), entries.size()));
        }

        chart.notifyDataSetChanged();
        chart.invalidate();
    }



    private void addChartEntry(LineChart chart, SensorData data, int[] colors) {
        // 获取主线程Handler
        Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(() -> {
            LineData lineData = chart.getData();
            if (lineData == null) return;

            long time = data.getTimestamp() % 100000;
            float[] values = data.getValues();

            // 同步修改数据
            synchronized (lineData) {
                for (int i = 0; i < 3; i++) {
                    LineDataSet set = (LineDataSet) lineData.getDataSetByIndex(i);

                    // 使用安全方式操作数据
                    List<Entry> entries = new ArrayList<>(set.getValues());
                    if (entries.size() > MAX_DATA_POINTS) {
                        entries.remove(0);
                    }
                    entries.add(new Entry(time, values[i]));

                    set.setValues(entries); // 原子性更新整个数据集
                }
            }

            // 通知更新
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