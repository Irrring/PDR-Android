package com.example.newpdr.DataClass;

import com.amap.api.maps.model.LatLng;

public class PDRPoint {

    public double x; // 东向位移（米）
    public double y; // 北向位移（米）
    public long timestamp;
    public double accuracy; // 定位精度估计

    // 新增构造方法
    public PDRPoint() {}

    public PDRPoint(double x, double y, long timestamp, double accuracy) {
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.accuracy = accuracy;
    }


    /**
     * 将局部 NE（North, East）偏移量转换为 GCJ-02 下的经纬度坐标（BLH，H假设为0）。
     *
     * @param origin 原点经纬度（BLH中的B和L，H假设为0），必须已经在GCJ-02坐标系下
     * @return 转换后的经纬度坐标（BLH，H=0）
     */
    public LatLng toLatLng(LatLng origin) {

        // 使用地球半径（单位：米），此处取 WGS84 近似值，适用于小范围偏移
        double earthRadius = 6378137.0;
        double dLat = (y / earthRadius) * (180 / Math.PI);
        double dLng = (x / (earthRadius * Math.cos(Math.toRadians(origin.latitude)))) * (180 / Math.PI);
        double newLat = origin.latitude + dLat;
        double newLng = origin.longitude + dLng;
        return new LatLng(newLat, newLng);
    }

}
