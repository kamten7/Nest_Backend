package com.nest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 退租结算（房东确认退押金）请求体（deductAmount缺省0全额退） */
@Data
public class RentRefundDTO {

    @DecimalMin(value = "0.00", message = "扣款金额不能为负数")
    @Digits(integer = 10, fraction = 2, message = "扣款金额格式不正确")
    private BigDecimal deductAmount;

    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
