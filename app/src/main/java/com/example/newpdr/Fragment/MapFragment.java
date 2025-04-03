package com.example.newpdr.Fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.MarkerOptions;
import com.example.newpdr.R;
import com.example.newpdr.DataClass.PositionView;
import com.example.newpdr.DataClass.PDRPoint;
import com.example.newpdr.ViewModel.SensorViewModel;
import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment {
    private SensorViewModel viewModel;
    private PositionView positionView;
    private MapView mapView;
    private AMap aMap;

    // 用于显示 PDR 轨迹（蓝色）的 Polyline
    private Polyline pdrPolyline;
    // 用于存储 PDR 推算轨迹点（局部坐标转换后的经纬度）
    private final List<LatLng> pdrTrackPoints = new ArrayList<>();

    // 用于显示高德定位轨迹（红色）的 Polyline
    private Polyline gaodePolyline;
    // 用于存储高德定位轨迹点（本地累计）
    private final List<LatLng> gaodeTrackPoints = new ArrayList<>();

    // 配置：红色表示高德定位轨迹，蓝色表示 PDR 解算轨迹；线宽 12，初始缩放级别 20f
    private static final int GAODE_POLYLINE_COLOR = Color.RED;
    private static final int PDR_POLYLINE_COLOR = Color.BLUE;
    private static final float POLYLINE_WIDTH = 12f;
    private static final float INITIAL_ZOOM = 20f;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        // 初始化局部坐标显示
        positionView = rootView.findViewById(R.id.positionView);

        // 初始化 MapView
        mapView = rootView.findViewById(R.id.amapView);
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();

        // 设置底图参数：例如使用卫星图、启用倾斜与旋转手势
        if (aMap != null) {
            aMap.setMapType(AMap.MAP_TYPE_SATELLITE);
            aMap.getUiSettings().setTiltGesturesEnabled(true);
            aMap.getUiSettings().setRotateGesturesEnabled(true);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SensorViewModel.class);

        // Observer 1：观察 PDR 推算的点，更新蓝色 PDR 轨迹
        viewModel.getLatestPdrPoint().observe(getViewLifecycleOwner(), pdrPoint -> {
            if (pdrPoint != null && aMap != null) {
                // 更新局部坐标显示
                float logicalX = (float) pdrPoint.x;
                float logicalY = - (float) pdrPoint.y;  // 根据需要调整符号
                positionView.updatePosition(logicalX, logicalY);

                // 获取局部坐标原点（BLH_Origin），用于转换
                LatLng origin = viewModel.get_BLH_Origin();
                if (origin != null) {
                    // 将 PDR 点转换为经纬度（假设 toLatLng 内部已处理 GCJ-02 转换）
                    LatLng convertedLatLng = pdrPoint.toLatLng(origin);
                    // 添加转换后的点到 PDR 轨迹列表
                    pdrTrackPoints.add(convertedLatLng);
                    // 更新或创建 PDR 轨迹的 Polyline（蓝色）
                    if (pdrPolyline == null) {
                        pdrPolyline = aMap.addPolyline(new PolylineOptions()
                                .addAll(pdrTrackPoints)
                                .width(POLYLINE_WIDTH)
                                .color(PDR_POLYLINE_COLOR));
                    } else {
                        pdrPolyline.setPoints(pdrTrackPoints);
                    }
                    // 更新摄像头视角到最新的 PDR 点
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(convertedLatLng, INITIAL_ZOOM));
                }
            }
        });

        // Observer 2：观察最新的高德定位数据，更新红色高德定位轨迹
        viewModel.get_GaoDe_Location().observe(getViewLifecycleOwner(), gaodeLocation -> {

            LatLng origin = viewModel.get_BLH_Origin();

            if (gaodeLocation != null && aMap != null && origin != null) {
                // 将最新的高德定位点添加到本地轨迹列表中
                gaodeTrackPoints.add(gaodeLocation);
                // 更新或创建高德定位轨迹的 Polyline（红色）

                if (gaodePolyline == null) {
                    gaodePolyline = aMap.addPolyline(new PolylineOptions()
                            .addAll(gaodeTrackPoints)
                            .width(POLYLINE_WIDTH)
                            .color(GAODE_POLYLINE_COLOR));
                    // 初次更新时将摄像头移到该点，并设置初始缩放级别
                    aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gaodeLocation, INITIAL_ZOOM));
                } else {
                    gaodePolyline.setPoints(gaodeTrackPoints);
                    // 可选：更新摄像头视角到最新高德定位点
                    //aMap.moveCamera(CameraUpdateFactory.newLatLng(gaodeLocation));
                }
            }
        });
    }

    // MapView 生命周期管理
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }
    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
