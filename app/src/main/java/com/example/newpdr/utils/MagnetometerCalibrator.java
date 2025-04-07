package com.example.newpdr.utils;

import android.util.Log;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用最小二乘法进行椭球拟合的磁强计校准类
 * 拟合方程形式为：
 *    A*x^2 + B*y^2 + C*z^2 + D*x + E*y + F*z = 1
 *
 * 通过求解上述方程的参数，可以计算出偏置：
 *    x0 = -D/(2A),  y0 = -E/(2B),  z0 = -F/(2C)
 *
 * 并计算出各轴的半径：
 *    a = sqrt(R/A),  b = sqrt(R/B),  c = sqrt(R/C)
 * 其中 R = 1 + A*x0^2 + B*y0^2 + C*z0^2
 *
 * 软铁校正因子为：
 *    s_x = r_avg / a,   s_y = r_avg / b,   s_z = r_avg / c
 * 其中 r_avg = (a + b + c)/3
 */
public class MagnetometerCalibrator {

    // 存储采样数据，每个样本为一个三维点 {x, y, z}
    private final List<double[]> samples = new ArrayList<>();
    // 校准得到的偏置（硬铁校正）
    private double[] bias = new double[]{0.0,0.0,0.0};
    // 校准得到的缩放因子（软铁校正）
    private double[] scale = new double[]{1.0, 1.0, 1.0};
    private boolean calibrated = false;
    // 设定最小采样数量
    private static final int MIN_SAMPLES = 200;

    /**
     * 添加一个采样数据，要求数据为3维向量
     */
    public void addSample(float[] sample) {
        if (sample == null || sample.length < 3) {
            return;
        }
        samples.add(new double[]{sample[0], sample[1], sample[2]});
    }

    /**
     * 返回采样数量
     */
    public int getSampleCount() {
        return samples.size();
    }

    /**
     * 返回所有采样数据（直接引用，仅用于读取）
     */
    public List<double[]> getSamples() {
        return samples;
    }

    /**
     * 返回采样数据的质量信息：各轴的最小值、最大值、均值以及样本数量
     */
    public String getQualityInfo() {
        if (samples.isEmpty()) {
            return "无数据";
        }
        double[] min = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = new double[]{-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] sum = new double[]{0, 0, 0};

        for (double[] sample : samples) {
            for (int i = 0; i < 3; i++) {
                if (sample[i] < min[i]) min[i] = sample[i];
                if (sample[i] > max[i]) max[i] = sample[i];
                sum[i] += sample[i];
            }
        }
        double[] mean = new double[]{
                sum[0] / samples.size(),
                sum[1] / samples.size(),
                sum[2] / samples.size()
        };
        return String.format("采样数: %d\nX: [%.2f, %.2f] 均值: %.2f\nY: [%.2f, %.2f] 均值: %.2f\nZ: [%.2f, %.2f] 均值: %.2f",
                samples.size(), min[0], max[0], mean[0],
                min[1], max[1], mean[1],
                min[2], max[2], mean[2]);
    }

    /**
     * 判断是否已采集到足够的样本数据
     */
    public boolean isCalibrationReady() {
        return samples.size() >= MIN_SAMPLES;
    }

    /**
     * 使用最小二乘法进行椭球拟合，并计算校准参数
     * 返回 true 表示计算成功并更新了 bias 和 scale，否则返回 false。
     */
    public boolean computeCalibration() {
        if (!isCalibrationReady()) {
            return false;
        }
        int n = samples.size();

        // 检查数据分布是否均匀：计算各轴的最小值和最大值
        double[] min = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = new double[]{-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : samples) {
            for (int i = 0; i < 3; i++) {
                if (p[i] < min[i]) {
                    min[i] = p[i];
                }
                if (p[i] > max[i]) {
                    max[i] = p[i];
                }
            }
        }
        double rangeX = max[0] - min[0];
        double rangeY = max[1] - min[1];
        double rangeZ = max[2] - min[2];
        // 设定合理的阈值，具体数值需要根据实际设备情况调整
        double threshold = 20.0;
        if (rangeX < threshold || rangeY < threshold || rangeZ < threshold) {
            // 数据分布不均，拒绝校准
            return false;
        }

        double[][] M = new double[n][6];
        double[] B = new double[n];

        // 构造线性系统：M * params = B，其中 params = [A, B, C, D, E, F]
        for (int i = 0; i < n; i++) {
            double[] p = samples.get(i);
            double x = p[0], y = p[1], z = p[2];
            M[i][0] = x * x;
            M[i][1] = y * y;
            M[i][2] = z * z;
            M[i][3] = x;
            M[i][4] = y;
            M[i][5] = z;
            B[i] = 1.0;
        }

        double[] params = solveLeastSquaresUsingCommonsMath(M, B);
        if (params == null) {
            return false;
        }

        // 从求解结果中提取参数
        double A = params[0], B_coef = params[1], C = params[2];
        double D_coef = params[3], E_coef = params[4], F_coef = params[5];

        // 检查参数合理性，避免 A、B、C 过小导致计算异常
        if (Math.abs(A) < 1e-6 || Math.abs(B_coef) < 1e-6 || Math.abs(C) < 1e-6) {
            return false;
        }

        // 计算偏置： x0 = -D/(2A), y0 = -E/(2B), z0 = -F/(2C)
        double x0 = -D_coef / (2 * A);
        double y0 = -E_coef / (2 * B_coef);
        double z0 = -F_coef / (2 * C);

        // 计算常数 R = 1 + A*x0^2 + B*y0^2 + C*z0^2
        double R_const = 1.0 + A * x0 * x0 + B_coef * y0 * y0 + C * z0 * z0;
        if (R_const <= 0) {
            return false;
        }


        // 计算各轴半径
        double a = Math.sqrt(R_const / A);
        double b_val = Math.sqrt(R_const / B_coef);
        double c_val = Math.sqrt(R_const / C);
        // 再次检查半径是否有效
        if (Double.isNaN(a) || Double.isNaN(b_val) || Double.isNaN(c_val)) {
            return false;
        }


        // 更新硬铁校正偏置
        bias[0] = x0;
        bias[1] = y0;
        bias[2] = z0;

        // 计算平均半径，并得到各轴的缩放因子（软铁校正）
        double rAvg = (a + b_val + c_val) / 3.0;
        scale[0] = rAvg / a;
        scale[1] = rAvg / b_val;
        scale[2] = rAvg / c_val;

        Log.d("MagCali","Bias: " + x0 + " " + y0 + " " + z0 + "   Scale: " + scale[0] + " " + scale[1] + " " + scale[2] );



        calibrated = true;
        return true;
    }

    /**
     *  设置标定情况（如果不需要标定的话，需要直接设置为true）
     */
    public void setCalibrated(boolean isCali){
        calibrated = isCali;
    }



    /**
     * 判断当前是否已经校准
     */
    public boolean isCalibrated() {
        return calibrated;
    }

    /**
     * 返回校准得到的偏置（硬铁校正参数）
     */
    public double[] getBias() {
        return bias;
    }

    /**
     * 返回校准得到的缩放因子（软铁校正参数）
     */
    public double[] getScale() {
        return scale;
    }

    /**
     * 对输入的原始数据应用校准补偿
     * 如果尚未校准，则直接返回原数据
     */
    public float[] applyCalibration(float[] raw) {
        if (!calibrated) {
            return raw;
        }
        float[] calibratedData = new float[3];
        for (int i = 0; i < 3; i++) {
            calibratedData[i] = (float)((raw[i] - bias[i]) * scale[i]);
        }
        return calibratedData;
    }

    /**
     * 使用 Apache Commons Math 的 OLSMultipleLinearRegression 求解最小二乘问题
     * M 为数据矩阵（n x 6），B 为目标向量（n维）
     */
    private double[] solveLeastSquaresUsingCommonsMath(double[][] M, double[] B) {
        try {
            OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
            regression.setNoIntercept(true);
            regression.newSampleData(B, M);
            return regression.estimateRegressionParameters();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
