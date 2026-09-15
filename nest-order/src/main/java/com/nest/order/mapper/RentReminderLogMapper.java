package com.nest.order.mapper;

import com.nest.entity.RentReminderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/** 房租提醒去重日志 Mapper。 */
@Mapper
public interface RentReminderLogMapper {

    int insert(RentReminderLog log);

    /** 该订单该周期该日是否已提醒过 */
    int countByUnique(@Param("orderId") Long orderId,
                      @Param("remindPeriod") String remindPeriod,
                      @Param("remindDate") LocalDate remindDate);
}
