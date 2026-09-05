package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 退租申请记录实体。
 *
 * 记录退租申请时间、生效的已购租期末周期、押金退回状态。
 * 退租申请后停止提醒与缴费；等 effective_end_period 周期结束后由定时任务退押金。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentTermination {

    /** 主键 */
    private Long id;
    /** 所属订单 ID */
    private Long orderId;
    /** 退租申请人（租客） */
    private Long tenantId;
    /** 退租申请时间 */
    private LocalDateTime applyTime;
    /** 生效的已购租期末周期（申请时已买到的那一期） */
    private String effectiveEndPeriod;
    /** 押金退回：0 待退 / 1 已退 */
    private Integer refundStatus;
    /** 押金实际退回时间 */
    private LocalDateTime refundTime;
    /** 押金退回流水 id */
    private Long refundTxnId;
    /** 备注（退租原因等） */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
}
