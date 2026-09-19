package com.nest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 钱包金额请求体（充值 / 提现共用） */
@Data
public class WalletAmountDTO {

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "金额最多保留两位小数")
    private BigDecimal amount;

    /**
     * 幂等键（仅提现必填，充值不要求）：前端在「打开提现弹窗」时生成一次 UUID，
     * 同一弹窗内重复提交会命中同一键。是否必填由 service 层按业务判定（recharge/withdraw
     * 共用本 DTO，不能在注解层一刀切），缺失时提现会被拒绝。
     * <p>服务端以 {@code wallet_transaction.idem_key} 唯一索引兜底：双击 / 网络重试都不会重复扣款。</p>
     */
    @Size(max = 64, message = "幂等键过长")
    private String idempotencyKey;
}
