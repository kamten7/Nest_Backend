package com.nest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 地理编码请求体 */
@Data
public class GeocodeDTO {

    @NotBlank(message = "地址不能为空")
    @Size(max = 200, message = "地址长度不能超过 200 个字符")
    private String address;
}
