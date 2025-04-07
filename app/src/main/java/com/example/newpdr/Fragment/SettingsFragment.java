package com.example.newpdr.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
    private RadioGroup stepModelGroup;  // 需要监听的 RadioGroup
    private SharedPreferences preferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化UI组件
        initViews(view);
        loadSettings();

        // 设置每个输入框和开关的监听器，来实现自动保存
        setListeners();

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
        stepModelGroup = view.findViewById(R.id.step_model_group); // 初始化 RadioGroup

        preferences = requireActivity().getSharedPreferences("PDR_Settings", Context.MODE_PRIVATE);
    }

    private void setListeners() {
        magneticCalibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("calibrationRequired", isChecked).apply();
            showToast("设置已保存");
        });

        floorDetectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("floorDetection", isChecked).apply();
            showToast("设置已保存");
        });

        stepLengthInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                String stepLength = stepLengthInput.getText().toString();
                if (isValidDecimal(stepLength)) {
                    preferences.edit().putFloat("stepLength", Float.parseFloat(stepLength)).apply();
                    showToast("设置已保存");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        // 监听 stepModelGroup 的变化
        stepModelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String stepModel = checkedId == R.id.model_constant ? "constant" : "height";
            preferences.edit().putString("stepModel", stepModel).apply();
            showToast("步骤模型设置已保存");
        });

        stepWindowInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                String stepWindow = stepWindowInput.getText().toString();
                if (isValidInteger(stepWindow)) {
                    preferences.edit().putInt("stepWindow", Integer.parseInt(stepWindow)).apply();
                    showToast("设置已保存");
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
    }

    private void loadSettings() {
        magneticCalibrationSwitch.setChecked(preferences.getBoolean("calibrationRequired", false));
        floorDetectionSwitch.setChecked(preferences.getBoolean("floorDetection", false));

        stepLengthInput.setText(getFormattedValue(preferences.getFloat("stepLength", 0.75f)));
        heightInput.setText(getFormattedValue(preferences.getFloat("height", 1.75f)));
        stepWindowInput.setText(String.valueOf(preferences.getInt("stepWindow", 20)));
        magneticDeclinationInput.setText(getFormattedValue(preferences.getFloat("magneticDeclination", 0.0f)));
        initialFloorInput.setText(String.valueOf(preferences.getInt("initialFloor", 1)));
        floorHeightInput.setText(getFormattedValue(preferences.getFloat("floorHeight", 3.0f)));

        // 设置步骤模型选择的状态
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
