package com.nest.service;

import com.nest.vo.GeocodeVO;

/** Nominatim 地理编码服务 —— 地址↔坐标互转。 */
public interface NominatimService {

    /** 正向地理编码：地址→坐标。解析失败返回 null。 */
    GeocodeVO geocode(String address);

    /** 反向地理编码：坐标→地址。解析失败返回 null。 */
    GeocodeVO reverseGeocode(double lat, double lng);
}
