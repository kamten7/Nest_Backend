package com.nest.mapper;

import com.nest.entity.RentReminderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 房租提醒去重日志 Mapper。
 */
@Mapper
public interface RentReminderLogMapper {

    /** 新增提醒日志 */
    int insert(RentReminderLog log);

    /** 某订单某周期某天是否已提醒 */
    int countByOrderAndPeriod(@Param("orderId") Long orderId,
                              @Param("remindPeriod") String remindPeriod,
                              @Param("remindDate") LocalDate remindDate);
}
