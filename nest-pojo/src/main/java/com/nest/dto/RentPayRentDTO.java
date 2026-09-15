package com.nest.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 缴纳当月租金请求体（period 缺省取订单的 nextDuePeriod）。
 */
@Data
public class RentPayRentDTO {

    /** 要缴的周期，如 2026-10；不传则取订单的 nextDuePeriod */
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "周期格式应为 yyyy-MM")
    private String period;
}
