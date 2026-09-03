package com.nest.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建预约请求体。
 */
@Data
public class AppointmentCreateDTO {

    /** 房源 ID */
    private Long houseId;
    /** 联系电话 */
    private String contactPhone;
    /** 期望看房时间 */
    private LocalDateTime appointmentTime;
    /** 备注 */
    private String remark;
}
