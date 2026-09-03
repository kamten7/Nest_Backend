package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地理编码响应视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeVO implements Serializable {

    /** 纬度 */
    private Double latitude;
    /** 经度 */
    private Double longitude;
    /** 人类可读地址（Nominatim display_name） */
    private String displayName;
}
