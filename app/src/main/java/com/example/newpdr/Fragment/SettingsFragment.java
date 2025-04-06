package com.example.newpdr.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.newpdr.R;

public class SettingsFragment extends Fragment {

    private Switch magneticCalibrationSwitch, floorDetectionSwitch;
    private EditText stepLengthInput, heightInput, stepWindowInput, magneticDeclinationInput, initialFloorInput, floorHeightInput;
    private RadioGroup stepModelGroup;
    private Button btnSaveSettings;
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化UI组件
        initViews(view);
        loadSettings();

        btnSaveSettings.setOnClickListener(v -> saveSettings());
        return view;
    }

    private void initViews(View view) {
        magneticCalibrationSwitch = view.findViewById(R.id.magnetic_calibration_switch);
        floorDetectionSwitch = view.findViewById(R.id.floor_detection_switch);
        stepLengthInput = view.findViewById(R.id.step_length_input);
        heightInput = view.findViewById(R.id.height_input);
        stepWindowInput = view.findViewById(R.id.step_window_input);
        magneticDeclinationInput = view.findViewById(R.id.magnetic_declination_input);
        initialFloorInput = view.findViewById(R.id.initial_floor_input);
        floorHeightInput = view.findViewById(R.id.floor_height_input);
        stepModelGroup = view.findViewById(R.id.step_model_group);
        btnSaveSettings = view.findViewById(R.id.btn_save_settings);

        preferences = requireActivity().getSharedPreferences("PDR_Settings", Context.MODE_PRIVATE);
    }


    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit();

        // 传感器是否要校准
        editor.putBoolean("calibrationRequired", magneticCalibrationSwitch.isChecked());

        // 步长模型选择
        int selectedModelId = stepModelGroup.getCheckedRadioButtonId();
        if (selectedModelId == R.id.model_constant) {
            editor.putString("stepModel", "constant");
            String stepLength = stepLengthInput.getText().toString();
            if (!isValidDecimal(stepLength)) {
                showToast("请输入有效的步长值");
                return;
            }
            editor.putFloat("stepLength", Float.parseFloat(stepLength));
        } else if (selectedModelId == R.id.model_height) {
            editor.putString("stepModel", "height");
            String height = heightInput.getText().toString();
            if (!isValidDecimal(height)) {
                showToast("请输入有效的身高值");
                return;
            }
            editor.putFloat("height", Float.parseFloat(height));
        } else {
            showToast("请选择步长模型");
            return;
        }

        // 步长探测窗口
        String stepWindow = stepWindowInput.getText().toString();
        if (!isValidInteger(stepWindow)) {
            showToast("请输入有效的步长探测窗口");
            return;
        }
        editor.putInt("stepWindow", Integer.parseInt(stepWindow));

        // 磁偏角
        String magneticDeclination = magneticDeclinationInput.getText().toString();
        if (!isValidDecimal(magneticDeclination)) {
            showToast("请输入有效的磁偏角值");
            return;
        }
        editor.putFloat("magneticDeclination", Float.parseFloat(magneticDeclination));

        // 楼层探测
        editor.putBoolean("floorDetection", floorDetectionSwitch.isChecked());
        if (floorDetectionSwitch.isChecked()) {
            String initialFloor = initialFloorInput.getText().toString();
            String floorHeight = floorHeightInput.getText().toString();
            if (!isValidInteger(initialFloor)) {
                showToast("请输入有效的初始楼层");
                return;
            }
            if (!isValidDecimal(floorHeight)) {
                showToast("请输入有效的单层高度");
                return;
            }
            editor.putInt("initialFloor", Integer.parseInt(initialFloor));
            editor.putFloat("floorHeight", Float.parseFloat(floorHeight));
        }

        // 保存数据
        editor.apply();
        showToast("设置已保存");
    }

    private void loadSettings() {
        magneticCalibrationSwitch.setChecked(preferences.getBoolean("sensorCalibrated", false));
        floorDetectionSwitch.setChecked(preferences.getBoolean("floorDetection", false));

        stepLengthInput.setText(getFormattedValue(preferences.getFloat("stepLength", 0.75f)));
        heightInput.setText(getFormattedValue(preferences.getFloat("height", 1.75f)));
        stepWindowInput.setText(String.valueOf(preferences.getInt("stepWindow", 20)));
        magneticDeclinationInput.setText(getFormattedValue(preferences.getFloat("magneticDeclination", 0.0f)));
        initialFloorInput.setText(String.valueOf(preferences.getInt("initialFloor", 1)));
        floorHeightInput.setText(getFormattedValue(preferences.getFloat("floorHeight", 3.0f)));

        String stepModel = preferences.getString("stepModel", "constant");
        if ("constant".equals(stepModel)) {
            stepModelGroup.check(R.id.model_constant);
        } else {
            stepModelGroup.check(R.id.model_height);
        }
    }

    private boolean isValidDecimal(String value) {
        return !TextUtils.isEmpty(value) && value.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isValidInteger(String value) {
        return !TextUtils.isEmpty(value) && value.matches("\\d+");
    }

    private String getFormattedValue(float value) {
        return String.format("%.2f", value);
    }

    private void showToast(String message) {
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}
