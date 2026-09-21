package com.nest.order.task;

import com.nest.constant.RentConstant;
import com.nest.order.service.RentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** 押金自动退还任务。 */
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
        log.info("退租自动结算任务完成: date={}, 待处理={} 条, 成功={} 条（退租申请满 {} 天冷却期后仍未结算）",
                today, dueIds.size(), done, RentConstant.SETTLE_GRACE_DAYS);
    }
}
