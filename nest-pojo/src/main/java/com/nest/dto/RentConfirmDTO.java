package com.nest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 确认租房请求体（看房结束后创建订单） */
@Data
public class RentConfirmDTO {

    @NotNull(message = "预约ID不能为空")
    private Long appointmentId;
}
