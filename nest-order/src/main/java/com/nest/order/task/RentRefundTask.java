package com.nest.order.task;

import com.nest.constant.RentConstant;
import com.nest.order.service.RentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 押金自动退还任务。
 *
 * <p>针对「退租申请中、已购租期结束且房东超过 7 天未结算」的订单：押金由房东钱包全额退回租客钱包，
 * 避免房东长期不操作导致租客拿不回押金。
 *
 * <p>逐条调用 {@link RentOrderService#autoRefundOne(Long)}（各自独立事务），
 * 单条失败不影响其它记录；退款落库带 {@code refund_status=0} 条件，重复执行不会双倍退款。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentRefundTask {

    private final RentOrderService rentOrderService;

    @Scheduled(cron = "${nest.rent.refund-cron:0 0 11 * * ?}")
    public void refund() {
        LocalDate today = LocalDate.now();
        List<Long> dueIds = rentOrderService.listAutoRefundDueIds(today);
        if (dueIds.isEmpty()) {
            log.debug("押金自动退还任务: 无待处理记录, date={}", today);
            return;
        }
        int done = 0;
        for (Long id : dueIds) {
            try {
                if (rentOrderService.autoRefundOne(id)) {
                    done++;
                }
            } catch (Exception e) {
                log.error("押金自动退还失败: terminationId={}", id, e);
            }
        }
        log.info("押金自动退还任务完成: date={}, 待处理={} 条, 成功={} 条（宽限 {} 天）",
                today, dueIds.size(), done, RentConstant.SETTLE_GRACE_DAYS);
    }
}
