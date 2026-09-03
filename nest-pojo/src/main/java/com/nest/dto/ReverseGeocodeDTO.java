package com.nest.dto;

import lombok.Data;

/**
 * 反向地理编码请求体。
 */
@Data
public class ReverseGeocodeDTO {

    /** 纬度 */
    private Double lat;
    /** 经度 */
    private Double lng;
}
