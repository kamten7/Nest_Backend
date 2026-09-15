package com.nest.order.mapper;

import com.nest.entity.RentPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 租房支付记录 Mapper。
 */
@Mapper
public interface RentPaymentMapper {

    int insert(RentPayment payment);

    /** 某订单的全部缴费记录（按时间正序） */
    List<RentPayment> selectByOrder(@Param("orderId") Long orderId);
}
