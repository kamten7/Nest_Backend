package com.nest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 提前支付未来房租请求体（单次 1–5 个月） */
@Data
public class RentPayAheadDTO {

    @NotNull(message = "提前支付月数不能为空")
    @Min(value = 1, message = "至少提前支付 1 个月")
    @Max(value = 5, message = "单次最多提前支付 5 个月")
    private Integer months;
}
