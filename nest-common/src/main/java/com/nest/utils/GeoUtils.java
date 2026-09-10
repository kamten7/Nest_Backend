package com.nest.utils;

/** 地理位置工具：Haversine 距离计算、坐标范围粗筛。 */
public final class GeoUtils {

    private GeoUtils() {}

    /** 地球半径（米） */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** 计算两点间距离（米），Haversine 公式。 */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** 格式化为可读距离（"1.2km" 或 "800m"）。 */
    public static String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format("%.1fkm", meters / 1000);
        }
        return String.format("%.0fm", meters);
    }

    /** 计算经纬度范围（SQL 粗筛用）。返回 [minLat, maxLat, minLng, maxLng]。 */
    public static double[] boundingBox(double lat, double lng, double radius) {
        double latDelta = Math.toDegrees(radius / EARTH_RADIUS_M);
        double lngDelta = Math.toDegrees(radius / (EARTH_RADIUS_M * Math.cos(Math.toRadians(lat))));
        return new double[]{
                lat - latDelta,
                lat + latDelta,
                lng - lngDelta,
                lng + lngDelta
        };
    }
}
