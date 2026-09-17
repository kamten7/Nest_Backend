package com.nest.order.mapper;

import com.nest.entity.RentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 租房订单 Mapper。 */
@Mapper
public interface RentOrderMapper {

    RentOrder selectById(@Param("id") Long id);

    /** 按订单号查询（用于对账 / 幂等排查） */
    RentOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /** 按来源预约查询订单。用于「同一预约只能生成一个有效订单」的防重复校验 —— */
    RentOrder selectByAppointmentId(@Param("appointmentId") Long appointmentId);

    int insert(RentOrder order);

    /** 租客端订单列表（status 为空=全部） */
    List<RentOrder> selectByTenant(@Param("tenantId") Long tenantId, @Param("status") Integer status);

    /** 房东端订单列表（status 为空=全部） */
    List<RentOrder> selectByLandlord(@Param("landlordId") Long landlordId, @Param("status") Integer status);

    /** 缴押金成功：待缴押金(1) → 租房中(2)，并落起租日与首个待缴周期 */
    int activateAfterDeposit(@Param("id") Long id,
                             @Param("startDate") LocalDate startDate,
                             @Param("nextDuePeriod") String nextDuePeriod);

    /**
     * 缴租成功：后移待缴周期 + 累加已缴月数。
     */
    int advanceAfterRentPaid(@Param("id") Long id,
                             @Param("expectedPeriod") String expectedPeriod,
                             @Param("nextDuePeriod") String nextDuePeriod,
                             @Param("addMonths") int addMonths);

    /** 申请退租：租房中(2) → 退租申请中(3) */
    int toTerminating(@Param("id") Long id);

    /** 退租结算完成：退租申请中(3) → 已退租(4) */
    int toTerminated(@Param("id") Long id);

    /** 放弃租房：待缴押金(1) → 已取消(5) */
    int toCancelled(@Param("id") Long id);

    /** 待提醒订单：租房中、且今天已进入「待缴周期起始日 − beforeDays 天」 */
    List<RentOrder> selectDueForReminder(@Param("today") LocalDate today,
                                        @Param("beforeDays") int beforeDays);

    /** 房东「在租」订单的押金总额 —— 这部分钱在房东钱包里但不可提现 */
    BigDecimal sumLockedDeposit(@Param("landlordId") Long landlordId,
                                @Param("statuses") int[] statuses);
}
