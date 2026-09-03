package com.nest.service;

import com.nest.vo.GeocodeVO;

/**
 * Nominatim 地理编码服务 —— 地址 ↔ 坐标互转。
 */
public interface NominatimService {

    /**
     * 正向地理编码：地址 → 坐标。
     *
     * @param address 地址字符串，如 '杭州市西湖区文三路'
     * @return 坐标及可读地址，解析失败返回 null
     */
    GeocodeVO geocode(String address);

    /**
     * 反向地理编码：坐标 → 地址。
     *
     * @param lat 纬度
     * @param lng 经度
     * @return 坐标及可读地址，解析失败返回 null
     */
    GeocodeVO reverseGeocode(double lat, double lng);
}
