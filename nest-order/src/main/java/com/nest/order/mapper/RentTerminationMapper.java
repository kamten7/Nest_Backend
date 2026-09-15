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

    /** 标记押金已退回。带  条件：并发/重复调用时只有一次能成功， */
    int markRefunded(@Param("id") Long id,
                     @Param("deductAmount") BigDecimal deductAmount,
                     @Param("refundAmount") BigDecimal refundAmount,
                     @Param("refundTime") LocalDateTime refundTime,
                     @Param("refundTxnId") Long refundTxnId,
                     @Param("remark") String remark);

    /** 待自动结算的退租记录：仍未退款，且「已购租期末周期」的月末 + 宽限期已过 */
    List<RentTermination> selectAutoRefundDue(@Param("today") LocalDate today,
                                             @Param("graceDays") int graceDays);
}
