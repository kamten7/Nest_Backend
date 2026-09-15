package com.nest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 确认租房时的源数据（预约 + 房源 + 封面）内部查询结果。
 *
 * <p>由 nest-order 自己的 Mapper 查询，避免反向依赖 nest-server 的 AppointmentMapper / HouseMapper。
 * 房东 ID、押金、月租一律以库中房源为准，<b>不信任前端传参</b>。
 */
@Data
public class RentSourceDTO {

    // ---- 预约 ----
    private Long appointmentId;
    private Long appointmentTenantId;
    /** 预约状态：1 待确认 / 2 已确认 / 3 已看房 / 4 已取消 / 5 已成交 */
    private Integer appointmentStatus;

    // ---- 房源 ----
    private Long houseId;
    private Long houseLandlordId;
    private String houseTitle;
    private String houseCover;
    /** 房源挂出来的月租金（house.price） */
    private BigDecimal housePrice;
    /** 房源押金（house.deposit，可能为空） */
    private BigDecimal houseDeposit;
    /** 房源状态：1 上架 / 0 下架 */
    private Integer houseStatus;
}
