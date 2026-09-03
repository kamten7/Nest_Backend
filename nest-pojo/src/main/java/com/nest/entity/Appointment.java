package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预约看房实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    private Long id;
    /** 租客ID */
    private Long tenantId;
    /** 房源ID */
    private Long houseId;
    /** 房东ID */
    private Long landlordId;
    /** 联系电话 */
    private String contactPhone;
    /** 期望看房时间 */
    private LocalDateTime appointmentTime;
    /** 备注 */
    private String remark;
    /** 状态：1 待确认，2 已确认，3 已看房，4 已取消，5 已成交 */
    private Integer status;
    /** 取消原因 */
    private String cancelReason;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
