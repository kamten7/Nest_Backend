package com.nest.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租房订单视图（租客端 / 房东端共用）。
 *
 * <p>字段与 `miniapp/api/rent.js`、`frontend/src/api/rent.ts` 的约定保持一致：
 * 列表页需要 {@code id/orderNo/houseTitle/deposit/monthlyRent/status/nextDuePeriod/paidMonths}，
 * 详情页追加 {@code startDate/payments/termination}，房东端还使用 {@code houseCover}。
 */
@Data
public class RentOrderVO {

    private Long id;
    /** 订单号 */
    private String orderNo;
    private Long houseId;
    /** 房源标题 */
    private String houseTitle;
    /** 房源封面（房东端列表使用） */
    private String houseCover;
    /** 押金 */
    private BigDecimal deposit;
    /** 月租金 */
    private BigDecimal monthlyRent;
    /** 状态：1 待缴押金 / 2 租房中 / 3 退租申请中 / 4 已退租 / 5 已取消 */
    private Integer status;
    /** 起租日 */
    private LocalDate startDate;
    /** 下次待缴周期 */
    private String nextDuePeriod;
    /** 已缴月数 */
    private Integer paidMonths;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 缴费记录（详情返回） */
    private List<RentPaymentVO> payments;
    /** 退租信息（无退租时为 null） */
    private RentTerminationVO termination;
}
