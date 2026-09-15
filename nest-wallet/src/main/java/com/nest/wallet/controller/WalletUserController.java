package com.nest.wallet.controller;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.constant.JwtConstant;
import com.nest.dto.WalletAmountDTO;
import com.nest.vo.WalletTransactionVO;
import com.nest.vo.WalletVO;
import com.nest.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 租客端钱包接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/wallet")
@RequiredArgsConstructor
@Tag(name = "租客端-钱包", description = "余额查询/充值/提现/流水")
public class WalletUserController {

    private final WalletService walletService;

    /**
     * 查询我的钱包余额。
     */
    @GetMapping
    @Operation(summary = "查询我的钱包", description = "返回当前租客的钱包余额")
    public Result<WalletVO> getMyWallet() {
        WalletVO vo = walletService.getWalletVO(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId());
        return Result.success(vo);
    }

    /**
     * 充值（模拟，预留微信支付）。
     */
    @PostMapping("/recharge")
    @Operation(summary = "充值", description = "模拟充值，当前不调微信支付")
    public Result<WalletVO> recharge(@Valid @RequestBody WalletAmountDTO dto) {
        log.info("钱包充值: tenantId={}, amount={}", BaseContext.getCurrentId(), dto.getAmount());
        WalletVO vo = walletService.recharge(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), dto.getAmount());
        return Result.success("充值成功", vo);
    }

    /**
     * 提现（预留微信零钱到账）。
     */
    @PostMapping("/withdraw")
    @Operation(summary = "提现", description = "提交提现申请，落处理中流水，暂未实际打款")
    public Result<WalletVO> withdraw(@Valid @RequestBody WalletAmountDTO dto) {
        log.info("钱包提现: tenantId={}, amount={}", BaseContext.getCurrentId(), dto.getAmount());
        WalletVO vo = walletService.withdraw(JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), dto.getAmount());
        return Result.success("提现申请已提交", vo);
    }

    /**
     * 钱包流水（分页，可按业务类型筛选）。
     */
    @GetMapping("/transactions")
    @Operation(summary = "钱包流水", description = "分页查询收支明细，可按业务类型筛选")
    public Result<PageResult<WalletTransactionVO>> transactions(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WalletTransactionVO> result = walletService.listTransactions(
                JwtConstant.TYPE_TENANT, BaseContext.getCurrentId(), bizType, page, pageSize);
        return Result.success(result);
    }
}
