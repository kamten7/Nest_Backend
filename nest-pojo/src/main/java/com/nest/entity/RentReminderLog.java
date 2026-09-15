package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 房租提醒去重日志（唯一索引order_id+period） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentReminderLog {

    private Long id;
    private Long orderId;
    private String remindPeriod;
    private LocalDate remindDate;
    private LocalDateTime createTime;
}
