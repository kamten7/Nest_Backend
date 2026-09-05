package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 租房订单实体（主单）。
 *
 * 包含押金、月租、起租日、状态机与缴费进度；
 * landlordId 从房源带出，作为房款入账对象（不信任前端）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentOrder {

    /** 主键 */
    private Long id;
    /** 订单号 */
    private String orderNo;
    /** 来源预约 ID */
    private Long appointmentId;
    /** 租客 ID */
    private Long tenantId;
    /** 房源 ID */
    private Long houseId;
    /** 房东 ID（房款入账对象） */
    private Long landlordId;
    /** 押金（可退） */
    private BigDecimal deposit;
    /** 月租 */
    private BigDecimal monthlyRent;
    /** 状态：1 待缴押金 / 2 租房中 / 3 退租申请中 / 4 已退租 / 5 已取消 */
    private Integer status;
    /** 起租日 */
    private LocalDate startDate;
    /** 下次待缴周期，如 2026-10；提前支付 N 期则前移 N 月 */
    private String nextDuePeriod;
    /** 已缴月数 */
    private Integer paidMonths;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
