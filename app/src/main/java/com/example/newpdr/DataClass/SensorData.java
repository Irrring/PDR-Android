package com.example.newpdr.DataClass;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SensorData {
    public enum SensorType { ACCEL, GYRO, MAG, PRESSURE }

    private final long timestamp;
    private final SensorType type;
    private final float[] values;

    public SensorData(long timestamp, SensorType type, float[] values) {
        this.timestamp = timestamp;
        this.type = type;
        this.values = values;
    }

    // 类型转换方法
    public AccelData toAccelData() {
        if (type != SensorType.ACCEL) throw new IllegalStateException();
        return new AccelData(timestamp, values[0], values[1], values[2]);
    }

    public GyroData toGyroData() {
        if (type != SensorType.GYRO) throw new IllegalStateException();
        return new GyroData(timestamp, values[0], values[1], values[2]);
    }

    // 其他类型转换方法...
    public MagData toMagData() {
        if (type != SensorType.MAG) throw new IllegalStateException();
        return new MagData(timestamp, values[0], values[1], values[2]);
    }

    public PressureData toPressureData() {
        if (type != SensorType.PRESSURE) throw new IllegalStateException();
        return new PressureData(timestamp, values[0]);
    }

    @NonNull
    @Override
    public String toString() {
        String unit = "";
        String valueStr = "";

        switch (type) {
            case ACCEL:
                unit = "m/s²";
                valueStr = String.format(Locale.US, "X:%.3f, Y:%.3f, Z:%.3f",
                        values[0], values[1], values[2]);
                break;

            case GYRO:
                unit = "rad/s";
                valueStr = String.format(Locale.US, "X:%.5f, Y:%.5f, Z:%.5f",
                        values[0], values[1], values[2]);
                break;

            case MAG:
                unit = "μT";
                valueStr = String.format(Locale.US, "X:%.1f, Y:%.1f, Z:%.1f",
                        values[0], values[1], values[2]);
                break;

            case PRESSURE:
                unit = "hPa";
                valueStr = String.format(Locale.US, "%.2f", values[0]);
                break;
        }

        return String.format(Locale.US, "[%tT.%tL] %s %s %s",
                timestamp, timestamp,
                type.name(),
                valueStr,
                unit);
    }

    // Getter方法
    public long getTimestamp() { return timestamp; }
    public SensorType getType() { return type; }
    public float[] getValues() { return values; }


}