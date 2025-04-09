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
import android.widget.RadioGroup;
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
    private SharedPreferences preferences;
    private Button saveSettingsButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // 初始化UI组件
        initViews(view);
        loadSettings();
        setListeners();

        return view;
    }

    /**
     * 初始化视图组件
     * @param view Fragment的根视图
     */
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
        saveSettingsButton = view.findViewById(R.id.btn_save_settings);

        preferences = requireActivity().getSharedPreferences("PDR_Settings", Context.MODE_PRIVATE);
    }

    /**
     * 设置所有输入控件和开关的监听器
     */
    private void setListeners() {
        // 磁强计校准开关监听器
        magneticCalibrationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("calibrationRequired", isChecked).apply();
            showToast("magneticCalibration", isChecked);
        });

        // 楼层检测开关监听器
        floorDetectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean("floorDetection", isChecked).apply();
            showToast("floorDetection", isChecked);
        });

        // 步长模型选择监听器
        stepModelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String stepModel = checkedId == R.id.model_constant ? "constant" : "height";
            preferences.edit().putString("stepModel", stepModel).apply();
            showToast("stepModel", true);
        });

        // 使用 OnFocusChangeListener 监听 EditText 的焦点变化
        stepLengthInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(stepLengthInput, "stepLength", true, false);
            }
        });

        heightInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(heightInput, "height", true, false);
            }
        });

        stepWindowInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(stepWindowInput, "stepWindow", false, true);
            }
        });

        magneticDeclinationInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(magneticDeclinationInput, "magneticDeclination", true, false);
            }
        });

        initialFloorInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(initialFloorInput, "initialFloor", false, true);
            }
        });

        floorHeightInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveEditTextValue(floorHeightInput, "floorHeight", true, false);
            }
        });

        // 保存设置按钮监听器
        saveSettingsButton.setOnClickListener(v -> showToast("saveSettings", true));
    }

    /**
     * 从 SharedPreferences 加载设置并初始化 UI
     */
    private void loadSettings() {
        magneticCalibrationSwitch.setChecked(preferences.getBoolean("calibrationRequired", false));
        floorDetectionSwitch.setChecked(preferences.getBoolean("floorDetection", false));

        stepLengthInput.setText(getFormattedValue(preferences.getFloat("stepLength", 0.75f)));
        heightInput.setText(getFormattedValue(preferences.getFloat("height", 1.75f)));
        stepWindowInput.setText(String.valueOf(preferences.getInt("stepWindow", 20)));
        magneticDeclinationInput.setText(getFormattedValue(preferences.getFloat("magneticDeclination", -3.3f)));
        initialFloorInput.setText(String.valueOf(preferences.getInt("initialFloor", 1)));
        floorHeightInput.setText(getFormattedValue(preferences.getFloat("floorHeight", 3.0f)));

        String stepModel = preferences.getString("stepModel", "constant");
        if ("constant".equals(stepModel)) {
            stepModelGroup.check(R.id.model_constant);
        } else {
            stepModelGroup.check(R.id.model_height);
        }
    }

    /**
     * 保存 EditText 的值并显示提示
     * @param editText 输入框控件
     * @param key SharedPreferences 的键
     * @param isFloat 是否为浮点数
     * @param isInteger 是否为整数
     */
    private void saveEditTextValue(EditText editText, String key, boolean isFloat, boolean isInteger) {
        String value = editText.getText().toString();
        if (isFloat && isValidDecimal(value)) {
            preferences.edit().putFloat(key, Float.parseFloat(value)).apply();
            showToast(key, true);
        } else if (isInteger && isValidInteger(value)) {
            preferences.edit().putInt(key, Integer.parseInt(value)).apply();
            showToast(key, true);
        } else if (!TextUtils.isEmpty(value)) {
            // 如果输入无效，可以选择提示用户
            Toast.makeText(getActivity(), "输入格式错误，请检查", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 验证输入是否为有效的十进制数
     * @param value 输入字符串
     * @return 是否有效
     */
    private boolean isValidDecimal(String value) {
        return !TextUtils.isEmpty(value) && value.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 验证输入是否为有效的整数
     * @param value 输入字符串
     * @return 是否有效
     */
    private boolean isValidInteger(String value) {
        return !TextUtils.isEmpty(value) && value.matches("\\d+");
    }

    /**
     * 格式化浮点数为两位小数
     * @param value 浮点数值
     * @return 格式化后的字符串
     */
    private String getFormattedValue(float value) {
        return String.format("%.2f", value);
    }

    /**
     * 显示提示信息
     * @param settingName 设置项名称
     * @param isEnabled 是否启用（针对开关）或保存成功（针对输入）
     */
    private void showToast(String settingName, boolean isEnabled) {
        String message = "";
        switch (settingName) {
            case "magneticCalibration":
                message = isEnabled ? "磁强计校准已开启" : "磁强计校准已关闭";
                break;
            case "floorDetection":
                message = isEnabled ? "楼层检测已开启" : "楼层检测已关闭";
                break;
            case "stepLength":
                message = "步长已保存";
                break;
            case "height":
                message = "身高已保存";
                break;
            case "stepWindow":
                message = "步长窗口已保存";
                break;
            case "magneticDeclination":
                message = "磁偏角已保存";
                break;
            case "initialFloor":
                message = "初始楼层已保存";
                break;
            case "floorHeight":
                message = "楼层高度已保存";
                break;
            case "stepModel":
                message = "步长模型已保存";
                break;
            default:
                message = "设置已自动保存";
                break;
        }
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }
}