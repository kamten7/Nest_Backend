package com.nest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 反向地理编码请求体 */
@Data
public class ReverseGeocodeDTO {

    @NotNull(message = "纬度不能为空")
    private Double lat;
    @NotNull(message = "经度不能为空")
    private Double lng;
}
