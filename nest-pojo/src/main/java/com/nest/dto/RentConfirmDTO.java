package com.nest.dto;

import lombok.Data;

/**
 * 确认租房（创建订单）请求体。
 */
@Data
public class RentConfirmDTO {

    /** 来源看房预约 ID */
    private Long appointmentId;
}
