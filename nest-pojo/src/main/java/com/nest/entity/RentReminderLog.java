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
 * <p>唯一索引 {@code uk_order_period(order_id, remind_period, remind_date)} 保证
 * 「同一订单 + 同一待缴周期 + 同一天」只提醒一次。任务必须先插日志、插入成功才推送。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentReminderLog {

    private Long id;
    /** 被提醒的订单 */
    private Long orderId;
    /** 提醒针对的待缴周期，如 2026-10 */
    private String remindPeriod;
    /** 提醒日期 */
    private LocalDate remindDate;
    private LocalDateTime createTime;
}
