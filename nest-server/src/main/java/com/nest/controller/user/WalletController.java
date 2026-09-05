package com.nest.controller.user;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.constant.JwtConstant;
import com.nest.dto.WalletRechargeDTO;
import com.nest.dto.WalletWithdrawDTO;
import com.nest.service.WalletService;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 租客端钱包接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/wallet")
@RequiredArgsConstructor
@Tag(name = "租客端-钱包", description = "余额查询/充值/提现/流水")
public class WalletController {

    private final WalletService walletService;

    /** 查询我的钱包（余额） */
    @GetMapping
    @Operation(summary = "我的钱包", description = "查询当前租客钱包余额（无则自动创建）")
    public Result<WalletVO> myWallet() {
        WalletVO vo = walletService.getMyWallet(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId());
        return Result.success(vo);
    }

    /**
     * 充值（模拟）。
     * 当前直接加余额；后期接微信支付后改为「下单 → 回调加余额」。
     */
    @PostMapping("/recharge")
    @Operation(summary = "充值", description = "模拟充值：直接加余额并记流水，预留微信支付")
    public Result<WalletVO> recharge(@RequestBody(required = false) WalletRechargeDTO dto) {
        BigDecimal amount = dto == null ? null : dto.getAmount();
        WalletVO vo = walletService.recharge(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), amount);
        return Result.success(vo);
    }

    /**
     * 提现（预留）。
     * 扣减余额并记一条「处理中」流水；后期对接微信零钱到账。
     */
    @PostMapping("/withdraw")
    @Operation(summary = "提现", description = "提现：扣减余额，状态=处理中（预留微信零钱到账）")
    public Result<WalletVO> withdraw(@RequestBody(required = false) WalletWithdrawDTO dto) {
        BigDecimal amount = dto == null ? null : dto.getAmount();
        WalletVO vo = walletService.withdraw(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), amount);
        return Result.success(vo);
    }

    /** 我的钱包流水（分页，可按业务类型筛选） */
    @GetMapping("/transactions")
    @Operation(summary = "钱包流水", description = "分页查询流水，可按业务类型筛选（RECHARGE/WITHDRAW 等）")
    public Result<PageResult<WalletTransactionVO>> transactions(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WalletTransactionVO> result = walletService.listTransactions(
                JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), bizType, page, pageSize);
        return Result.success(result);
    }
}
