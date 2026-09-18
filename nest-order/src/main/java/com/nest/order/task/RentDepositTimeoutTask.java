package com.nest.order.task;

import com.nest.constant.RentConstant;
import com.nest.order.service.RentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待缴押金超时自动取消任务。
 *
 * <p>确认租房时房源会被立刻置为「在租中(2)」。若租客此后一直不缴押金，房源会被无限期占住 ——
 * 别人预约不了，房东也手动下架不了（房源状态为 2 时 {@code updateStatus} 会被拒）。
 * 本任务把创建超过 {@link RentConstant#DEPOSIT_PAY_TIMEOUT_MINUTES} 分钟仍未缴押金的订单
 * 自动置为「已取消(5)」，并把房源恢复为「上架(1)」，让房源重新可被预约。</p>
 *
 * <p>默认每分钟扫描一次（超时判定为滑动窗口，最坏情况下订单在 30~31 分钟之间被取消，
 * 取决于扫描落点）。扫描本身幂等：只要订单已不是「待缴押金」就跳过。</p>
 *
 * <p>⚠️ 与项目其它定时任务一样，{@code @Scheduled} 无分布式锁 ⇒ 只能单实例部署。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentDepositTimeoutTask {

    private final RentOrderService rentOrderService;

    @Scheduled(cron = "${nest.rent.deposit-timeout-cron:0 * * * * ?}")
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
                // 逐条独立事务：单笔失败不影响其它订单
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
