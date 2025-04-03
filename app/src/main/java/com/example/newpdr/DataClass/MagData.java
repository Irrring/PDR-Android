package com.example.newpdr.DataClass;


public class MagData {
    private final long timestamp;
    private final float x;
    private final float y;
    private final float z;

    public MagData(long timestamp, float x, float y, float z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Getter方法
    public long getTimestamp() { return timestamp; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }

    public float[] getValues() {
        return new float[]{x,y,z};
    }
}