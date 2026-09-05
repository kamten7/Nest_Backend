package com.nest.mapper;

import com.nest.entity.RentTermination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退租申请 Mapper。
 */
@Mapper
public interface RentTerminationMapper {

    /** 新增退租申请，回填 ID */
    int insert(RentTermination termination);

    /** 查询订单的退租申请（若有） */
    RentTermination selectByOrderId(@Param("orderId") Long orderId);

    /** 查询待退押金的退租申请（定时任务扫） */
    List<RentTermination> selectPendingRefund();

    /** 更新押金退回状态 */
    int updateRefund(@Param("id") Long id,
                     @Param("refundStatus") Integer refundStatus,
                     @Param("refundTime") LocalDateTime refundTime,
                     @Param("refundTxnId") Long refundTxnId);
}
