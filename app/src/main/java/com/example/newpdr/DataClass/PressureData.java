package com.example.newpdr.DataClass;


public class PressureData {
    private final long timestamp;
    private final float value;

    public PressureData(long timestamp, float value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    // Getter方法
    public long getTimestamp() { return timestamp; }
    public float getValue() { return value; }
}