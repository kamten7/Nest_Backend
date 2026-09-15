package com.nest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退租结算（房东确认退押金）请求体。
 *
 * <p>{@code deductAmount} 可不传，缺省视为 0.00，即全额退回押金 —— 这样前端在
 * 未提供扣款输入框时也能正常走通（等价于不扣款）。
 */
@Data
public class RentRefundDTO {

    /** 从押金中扣除、归房东的金额（物品损坏等），不传=不扣款 */
    @DecimalMin(value = "0.00", message = "扣款金额不能为负数")
    @Digits(integer = 10, fraction = 2, message = "扣款金额格式不正确")
    private BigDecimal deductAmount;

    /** 扣款说明 / 备注 */
    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;
}
