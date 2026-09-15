package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 地理编码响应视图 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeVO implements Serializable {

    private Double latitude;
    private Double longitude;
    private String displayName;
}
