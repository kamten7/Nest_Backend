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

/** 房东端钱包接口（Header 使用 token 通道，由 JwtTokenAdminInterceptor 鉴权）。 */
@Slf4j
@RestController
@RequestMapping("/admin/wallet")
@RequiredArgsConstructor
@Tag(name = "房东端-钱包", description = "余额查询/提现/流水")
public class WalletAdminController {

    private final WalletService walletService;

    /** 查询我的钱包（余额 + 锁定押金 + 可提现余额）。 */
    @GetMapping
    @Operation(summary = "查询我的钱包", description = "返回房东的钱包余额、在租押金锁定金额与可提现余额")
    public Result<WalletVO> getMyWallet() {
        WalletVO vo = walletService.getWalletVO(JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId());
        return Result.success(vo);
    }

    /** 提现到微信零钱（预留）。 */
    @PostMapping("/withdraw")
    @Operation(summary = "提现", description = "提交提现申请；可提现金额 = 余额 − 在租订单押金")
    public Result<WalletVO> withdraw(@Valid @RequestBody WalletAmountDTO dto) {
        log.info("房东钱包提现: landlordId={}, amount={}", BaseContext.getCurrentId(), dto.getAmount());
        WalletVO vo = walletService.withdraw(JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId(), dto.getAmount());
        return Result.success("提现申请已提交", vo);
    }

    /** 钱包流水（分页，可按业务类型筛选）。 */
    @GetMapping("/transactions")
    @Operation(summary = "钱包流水", description = "分页查询收支明细，可按业务类型筛选")
    public Result<PageResult<WalletTransactionVO>> transactions(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WalletTransactionVO> result = walletService.listTransactions(
                JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId(), bizType, page, pageSize);
        return Result.success(result);
    }
}
