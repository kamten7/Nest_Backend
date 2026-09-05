package com.nest.task;

import com.nest.constant.JwtConstant;
import com.nest.constant.RentConstant;
import com.nest.constant.WalletConstant;
import com.nest.entity.RentOrder;
import com.nest.entity.RentTermination;
import com.nest.mapper.RentOrderMapper;
import com.nest.mapper.RentTerminationMapper;
import com.nest.service.WalletService;
import com.nest.utils.RentPeriodUtil;
import com.nest.vo.WalletTransferVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退租押金退回定时任务。
 *
 * 每天扫描退租申请（refund_status=0）：当今天 ≥ effectiveEndPeriod 周期结束时，
 * 押金由房东钱包退回租客钱包，并把订单置为已退租。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentRefundTask {

    private final RentTerminationMapper rentTerminationMapper;
    private final RentOrderMapper rentOrderMapper;
    private final WalletService walletService;

    /** 每天 03:00 执行 */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void refundDeposit() {
        List<RentTermination> terminations = rentTerminationMapper.selectPendingRefund();
        LocalDateTime now = LocalDateTime.now();

        for (RentTermination termination : terminations) {
            RentOrder order = rentOrderMapper.selectById(termination.getOrderId());
            if (order == null) {
                continue;
            }
            // 1. 未到生效租期结束则不退
            if (LocalDateTime.now().isBefore(RentPeriodUtil.endOf(termination.getEffectiveEndPeriod()).atTime(23, 59, 59))) {
                continue;
            }
            // 2. 押金由房东钱包退回租客钱包
            WalletTransferVO tf = walletService.transfer(
                    JwtConstant.TYPE_LANDLORD, order.getLandlordId(),
                    JwtConstant.TYPE_TENANT, order.getTenantId(),
                    order.getDeposit(), WalletConstant.BIZ_DEPOSIT_REFUND, WalletConstant.BIZ_DEPOSIT_REFUND,
                    order.getOrderNo() + "-refund", "押金退回");
            // 3. 更新退租申请状态 + 订单为已退租
            rentTerminationMapper.updateRefund(termination.getId(), 1, now, tf.getPayerTxnId());
            rentOrderMapper.updateStatus(order.getId(), RentConstant.ORDER_TERMINATED);

            log.info("退租押金退回: orderId={}, tenantId={}, deposit={}",
                    order.getId(), order.getTenantId(), order.getDeposit());
        }
    }
}
