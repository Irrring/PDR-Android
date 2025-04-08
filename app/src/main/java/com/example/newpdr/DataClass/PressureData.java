package com.example.newpdr.DataClass;


import org.json.JSONException;
import org.json.JSONObject;

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
    public JSONObject toJSON() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("timestamp", timestamp);
        obj.put("value", value);
        return obj;
    }
}