package com.nest.order.mapper;

import com.nest.entity.RentReminderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 房租提醒去重日志 Mapper。
 *
 * <p>唯一索引 {@code uk_order_period(order_id, remind_period, remind_date)} 是去重的唯一依据：
 * 任务必须<b>先插日志</b>，插入成功才推送，重复插入抛 {@code DuplicateKeyException} 直接跳过。
 */
@Mapper
public interface RentReminderLogMapper {

    int insert(RentReminderLog log);

    /** 该订单该周期该日是否已提醒过 */
    int countByUnique(@Param("orderId") Long orderId,
                      @Param("remindPeriod") String remindPeriod,
                      @Param("remindDate") LocalDate remindDate);
}
