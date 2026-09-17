package com.nest.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单模块「写预约状态」的 Mapper。
 */
@Mapper
public interface RentAppointmentMapper {

    /** 已看房(3) → 已成交(5)。条件更新，重复调用影响行数为 0（天然幂等）。 */
    int markDealIfVisited(@Param("appointmentId") Long appointmentId);

    /**
     * 已成交(5) → 已取消(4)。租客在「待缴押金」阶段放弃租房时回写。
     * 不回写会让预约列表里留下一条「已成交」但实际没有订单的记录，语义不实。
     * 条件更新，重复调用影响行数为 0（天然幂等）。
     */
    int markCancelledIfDeal(@Param("appointmentId") Long appointmentId,
                            @Param("reason") String reason);
}
