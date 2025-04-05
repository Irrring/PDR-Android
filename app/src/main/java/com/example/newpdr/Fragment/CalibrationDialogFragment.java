package com.example.newpdr.Fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.newpdr.R;
import com.example.newpdr.ViewModel.SensorViewModel;
import com.example.newpdr.DataClass.Magnetometer3DView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class CalibrationDialogFragment extends DialogFragment {

    private TextView tvCalibrationInfo;
    private MaterialButton btnStartCalibration;
    private Magnetometer3DView magnetometerView;
    private SensorViewModel viewModel;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateTask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_magnetometer_calibration, container, false);
        tvCalibrationInfo = view.findViewById(R.id.tvCalibrationInfo);
        btnStartCalibration = view.findViewById(R.id.btnStartCalibration);
        magnetometerView = view.findViewById(R.id.magnetometerView);

        viewModel = new ViewModelProvider(requireActivity()).get(SensorViewModel.class);

        btnStartCalibration.setOnClickListener(v -> {
            if (!viewModel.magnetometerCalibrator.isCalibrationReady()) {
                Toast.makeText(getContext(), "数据不足，请继续采集", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean success = viewModel.magnetometerCalibrator.computeCalibration();
            if (success) {
                viewModel.isCalibrating.postValue(false);
                Toast.makeText(getContext(), "磁强计标定完成，开始PDR解算", Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                Toast.makeText(getContext(), "标定失败，请继续采集数据，注意数据的分布均匀性", Toast.LENGTH_SHORT).show();
            }
        });

        updateTask = new Runnable() {
            @Override
            public void run() {
                String info = viewModel.magnetometerCalibrator.getQualityInfo();
                int count = viewModel.magnetometerCalibrator.getSampleCount();
                tvCalibrationInfo.setText(String.format("采集进度: %d 样本\n%s", count, info));

                // 更新3D图：转换采样数据格式（double[] -> float[]）
                List<double[]> samplesDouble = viewModel.magnetometerCalibrator.getSamples();
                List<float[]> samplesFloat = new ArrayList<>();
                for (double[] d : samplesDouble) {
                    samplesFloat.add(new float[]{(float)d[0], (float)d[1], (float)d[2]});
                }
                magnetometerView.updateSamples(samplesFloat);

                handler.postDelayed(this, 500);
            }
        };
        handler.post(updateTask);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(updateTask);
    }
}
