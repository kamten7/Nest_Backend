package com.nest.dto;

import lombok.Data;

/**
 * 缴纳当月租金请求体。
 */
@Data
public class RentPayRentDTO {

    /** 要缴的周期，如 2026-10；缺省取订单的 nextDuePeriod */
    private String period;
}
