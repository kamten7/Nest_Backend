package com.nest.dto;

import lombok.Data;

/**
 * 地理编码请求体。
 */
@Data
public class GeocodeDTO {

    /** 待解析的地址字符串 */
    private String address;
}
