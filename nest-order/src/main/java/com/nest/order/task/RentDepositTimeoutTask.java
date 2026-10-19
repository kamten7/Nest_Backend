package com.nest.order.task;

import com.nest.constant.RentConstant;
import com.nest.order.service.RentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentDepositTimeoutTask {

    private final RentOrderService rentOrderService;

    @Scheduled(cron = "${nest.rent.deposit-timeout-cron:0 * * * * ?}")
    @SchedulerLock(name = "rentDepositTimeoutTask", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cancelExpired() {
        int timeoutMinutes = RentConstant.DEPOSIT_PAY_TIMEOUT_MINUTES;

        List<Long> expired;
        try {
            expired = rentOrderService.listExpiredPendingDepositIds(timeoutMinutes);
        } catch (Exception e) {
            log.error("待缴押金超时扫描失败: 阈值={}分钟", timeoutMinutes, e);
            return;
        }
        if (expired.isEmpty()) {
            log.debug("待缴押金超时任务: 无待处理订单, 阈值={}分钟", timeoutMinutes);
            return;
        }

        int done = 0;
        for (Long id : expired) {
            try {

                if (rentOrderService.autoCancelExpiredOrder(id, timeoutMinutes)) {
                    done++;
                }
            } catch (Exception e) {
                log.error("待缴押金超时取消失败: orderId={}", id, e);
            }
        }
        log.info("待缴押金超时任务完成: 阈值={}分钟, 待处理={} 条, 已取消={} 条",
                timeoutMinutes, expired.size(), done);
    }
}
