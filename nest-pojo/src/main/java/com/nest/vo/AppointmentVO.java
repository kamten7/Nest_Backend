package com.nest.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约展示视图 —— 预约 + 房源 + 双方用户信息。
 */
@Data
public class AppointmentVO {

    /** 预约 ID */
    private Long id;
    /** 房源 ID */
    private Long houseId;
    /** 房源标题 */
    private String houseTitle;
    /** 房源封面 */
    private String houseCover;
    /** 房东 ID */
    private Long landlordId;
    /** 房东姓名 */
    private String landlordName;
    /** 租客 ID */
    private Long tenantId;
    /** 租客昵称 */
    private String tenantName;
    /** 联系电话 */
    private String contactPhone;
    /** 期望看房时间 */
    private LocalDateTime appointmentTime;
    /** 备注 */
    private String remark;
    /** 状态（1-5） */
    private Integer status;
    /** 状态文本 */
    private String statusText;
    /** 取消原因 */
    private String cancelReason;
    /** 创建时间 */
    private LocalDateTime createTime;
}
