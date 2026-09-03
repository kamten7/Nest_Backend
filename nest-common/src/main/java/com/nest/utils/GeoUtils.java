package com.nest.utils;

/**
 * 地理位置工具：Haversine 距离计算、坐标范围粗筛。
 * Haversine（半正矢）公式
 * 免费方案：不需要任何外部 API，纯数学计算。
 * 配合 Nominatim 地理编码（地址→坐标）+ Redis 缓存，实现零成本地图服务。
 */
public final class GeoUtils {

    private GeoUtils() {}

    /** 地球半径（米） */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /**
     * 计算两点间距离（米），使用 Haversine 公式。
     *
     * 为什么不用平面勾股定理？地球是球体，纬度/经度差不能直接当平面坐标。
     * Haversine 用球面三角算"大圆距离"，对找房场景精度足够（误差 < 0.5%）。
     *
     * @param lat1 点1纬度
     * @param lng1 点1经度
     * @param lat2 点2纬度
     * @param lng2 点2经度
     * @return 距离（米）
     */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        // 角度转弧度：Math 的 sin/cos 参数要求是弧度，而传入的是角度
        double dLat = Math.toRadians(lat2 - lat1);   // 纬度差（弧度）
        double dLng = Math.toRadians(lng2 - lng1);   // 经度差（弧度）
        // 半正矢核心公式：a 表示两点间"半中心角"的正弦平方和（0~1）
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        // 由 a 反推出中心夹角 c（两点相对地心的真实夹角，弧度）
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        // 弧长 = 半径 × 弧度，即两点在地球表面的大圆距离（米）
        return EARTH_RADIUS_M * c;
    }

    /**
     * 格式化为可读距离。
     *
     * @param meters 距离（米）
     * @return "1.2km" 或 "800m"
     */
    public static String formatDistance(double meters) {
        if (meters >= 1000) {
            // 满 1 公里用公里显示（保留 1 位小数）
            return String.format("%.1fkm", meters / 1000);
        }
        // 不足 1 公里直接用米显示（取整）
        return String.format("%.0fm", meters);
    }

    /**
     * 计算经纬度范围（用于 SQL 粗筛）。
     * 在指定中心点周围给定半径（米）划出矩形边界。
     *
     * 用途：地图"附近搜索"先画个矩形把候选圈进来（SQL BETWEEN，走索引快），
     * 再对圈内的少量房源用 Haversine 精算真实距离排序。
     *
     * @param lat    中心纬度
     * @param lng    中心经度
     * @param radius 半径（米）
     * @return [minLat, maxLat, minLng, maxLng]
     */
    public static double[] boundingBox(double lat, double lng, double radius) {
        // 纬度每差 1 度 ≈ 地球半径对应的弧长，用半径反推纬度差（度）
        double latDelta = Math.toDegrees(radius / EARTH_RADIUS_M);
        // 经度在纬度 lat 处一圈更短（cos(lat) 缩小），所以经度差要除以 cos(lat)
        double lngDelta = Math.toDegrees(radius / (EARTH_RADIUS_M * Math.cos(Math.toRadians(lat))));
        return new double[]{
                lat - latDelta,  // minLat 南边界
                lat + latDelta,  // maxLat 北边界
                lng - lngDelta,  // minLng 西边界
                lng + lngDelta   // maxLng 东边界
        };
    }
}
