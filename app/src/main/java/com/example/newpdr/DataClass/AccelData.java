package com.example.newpdr.DataClass;


public class AccelData {
    private final long timestamp;
    private final float x;
    private final float y;
    private final float z;

    public AccelData(long timestamp, float x, float y, float z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // 计算加速度模长
    public float getMagnitude() {
        return (float) Math.sqrt(x*x + y*y + z*z);
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
