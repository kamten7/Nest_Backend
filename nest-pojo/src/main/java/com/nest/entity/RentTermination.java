package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退租申请与结算记录实体。
 *
 * <p>提交流程：租客申请 → status=3、停止提醒与缴费；到 effective_end_period 周期结束后，
 * 房东可填扣款金额结算（{@code deductAmount} 归房东、{@code deposit - deductAmount} 退回租客），
 * 超期 7 天未结算则由定时任务自动全额退回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentTermination {

    private Long id;
    /** 所属订单 */
    private Long orderId;
    /** 申请退租的租客 ID */
    private Long tenantId;
    /** 退租申请时间 */
    private LocalDateTime applyTime;
    /** 生效的已购租期末周期（申请时 next_due_period 的上一个周期） */
    private String effectiveEndPeriod;
    /** 结算时从押金中扣除、归房东的金额（物品损坏等） */
    private BigDecimal deductAmount;
    /** 实际退回租客的押金金额（= 订单押金 − deduct_amount） */
    private BigDecimal refundAmount;
    /** 押金退回：0 待退 / 1 已退 */
    private Integer refundStatus;
    /** 押金实际退回时间 */
    private LocalDateTime refundTime;
    /** 押金退回流水 ID（租客侧收入流水） */
    private Long refundTxnId;
    /** 备注（退租原因 / 扣款明细说明） */
    private String remark;
    private LocalDateTime createTime;
}
