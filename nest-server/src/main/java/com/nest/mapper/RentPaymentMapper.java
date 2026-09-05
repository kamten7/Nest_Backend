package com.nest.mapper;

import com.nest.entity.RentPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租房支付记录 Mapper。
 */
@Mapper
public interface RentPaymentMapper {

    /** 新增支付记录，回填 ID */
    int insert(RentPayment payment);

    /** 查询订单的全部支付记录（押金 + 各期租金） */
    List<RentPayment> selectByOrderId(@Param("orderId") Long orderId);

    /** 某订单某周期是否已缴该类型（防重复缴租） */
    int countByOrderAndPayType(@Param("orderId") Long orderId,
                               @Param("payType") String payType,
                               @Param("period") String period);
}
