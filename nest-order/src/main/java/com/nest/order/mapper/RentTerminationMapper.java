package com.nest.order.mapper;

import com.nest.entity.RentTermination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 退租申请 / 结算 Mapper。 */
@Mapper
public interface RentTerminationMapper {

    int insert(RentTermination termination);

    RentTermination selectById(@Param("id") Long id);

    /** 某订单的退租记录（一单一退） */
    RentTermination selectByOrderId(@Param("orderId") Long orderId);

    /** 标记结算完成：写入押金退回/预付租金退回两笔金额与流水 ID。带 refund_status=0 条件，并发/重复调用时只有一次能成功。 */
    int markRefunded(@Param("id") Long id,
                     @Param("deductAmount") BigDecimal deductAmount,
                     @Param("refundAmount") BigDecimal refundAmount,
                     @Param("prepaidRefundAmount") BigDecimal prepaidRefundAmount,
                     @Param("refundTxnId") Long refundTxnId,
                     @Param("prepaidTxnId") Long prepaidTxnId,
                     @Param("refundTime") LocalDateTime refundTime,
                     @Param("remark") String remark);

    /** 待自动结算的退租记录：仍未退款，且退租申请时间已满冷却期（graceDays）。 */
    List<RentTermination> selectAutoRefundDue(@Param("today") LocalDate today,
                                             @Param("graceDays") int graceDays);
}
