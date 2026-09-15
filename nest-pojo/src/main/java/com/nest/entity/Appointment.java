package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 预约看房实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    private Long id;
    private Long tenantId;
    private Long houseId;
    private Long landlordId;
    private String contactPhone;
    private LocalDateTime appointmentTime;
    private String remark;
    private Integer status;
    private String cancelReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
