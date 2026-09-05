package com.nest.vo;

import com.nest.entity.RentPayment;
import com.nest.entity.RentTermination;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租房订单展示视图。
 *
 * 列表：仅基础字段 + 房源标题/封面；
 * 详情：额外带 payments（缴费记录）与 termination（退租信息）。
 */
@Data
public class RentOrderVO {

    /** 订单 ID */
    private Long id;
    /** 订单号 */
    private String orderNo;
    /** 房源 ID */
    private Long houseId;
    /** 房源标题 */
    private String houseTitle;
    /** 房源封面图 */
    private String houseCover;
    /** 押金 */
    private BigDecimal deposit;
    /** 月租 */
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

    // ==================== 详情附加 ====================

    /** 缴费记录（押金 + 各期租金） */
    private List<RentPayment> payments;
    /** 退租信息（无则 null） */
    private RentTermination termination;
}
