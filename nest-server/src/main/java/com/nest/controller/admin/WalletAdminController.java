package com.nest.controller.admin;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.constant.JwtConstant;
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
 * 房东端钱包接口 —— 余额查询 / 收款流水 / 提现。
 * 房东钱包主要用于接收租客缴纳的押金与房租，并支持提现到微信零钱。
 */
@Slf4j
@RestController
@RequestMapping("/admin/wallet")
@RequiredArgsConstructor
@Tag(name = "房东端-钱包", description = "余额查询/收款流水/提现")
public class WalletAdminController {

    private final WalletService walletService;

    /** 查询我的钱包（余额） */
    @GetMapping
    @Operation(summary = "我的钱包", description = "查询房东钱包余额（无则自动创建）")
    public Result<WalletVO> myWallet() {
        WalletVO vo = walletService.getMyWallet(JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId());
        return Result.success(vo);
    }

    /** 我的钱包流水（收款记录） */
    @GetMapping("/transactions")
    @Operation(summary = "钱包流水", description = "分页查询房东收款流水，可按业务类型筛选")
    public Result<PageResult<WalletTransactionVO>> transactions(
            @RequestParam(required = false) String bizType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<WalletTransactionVO> result = walletService.listTransactions(
                JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId(), bizType, page, pageSize);
        return Result.success(result);
    }

    /** 提现（预留微信零钱到账） */
    @PostMapping("/withdraw")
    @Operation(summary = "提现", description = "提现：扣减余额，状态=处理中（预留微信零钱到账）")
    public Result<WalletVO> withdraw(@RequestBody(required = false) WalletWithdrawDTO dto) {
        BigDecimal amount = dto == null ? null : dto.getAmount();
        WalletVO vo = walletService.withdraw(JwtConstant.TYPE_LANDLORD, BaseContext.getCurrentId(), amount);
        return Result.success(vo);
    }
}
