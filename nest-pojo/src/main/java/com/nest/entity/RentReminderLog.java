package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 房租提醒去重日志实体。
 *
 * 保证同一订单、同一待缴周期、同一天只提醒一次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentReminderLog {

    /** 主键 */
    private Long id;
    /** 被提醒的订单 ID */
    private Long orderId;
    /** 提醒针对的待缴周期 */
    private String remindPeriod;
    /** 提醒日期 */
    private LocalDate remindDate;
    /** 创建时间 */
    private LocalDateTime createTime;
}
