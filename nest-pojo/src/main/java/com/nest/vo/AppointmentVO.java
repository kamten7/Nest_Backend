package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 预约展示视图 —— 预约 + 房源 + 双方用户信息 */
@Data
public class AppointmentVO {

    private Long id;
    private Long houseId;
    private String houseTitle;
    private String houseCover;
    private Long landlordId;
    private String landlordName;
    private Long tenantId;
    private String tenantName;
    private String contactPhone;
    private LocalDateTime appointmentTime;
    private String remark;
    private Integer status;
    private String statusText;
    private String cancelReason;
    private LocalDateTime createTime;
}
