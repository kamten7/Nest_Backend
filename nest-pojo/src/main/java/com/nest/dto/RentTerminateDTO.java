package com.nest.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 申请退租请求体 */
@Data
public class RentTerminateDTO {

    @Size(max = 200, message = "退租原因不能超过 200 个字符")
    private String remark;
}
