package com.nest.order.task;

import com.nest.constant.RentConstant;
import com.nest.order.service.RentOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 房租到期提醒任务。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentReminderTask {

    private final RentOrderService rentOrderService;

    @Scheduled(cron = "${nest.rent.reminder-cron:0 0 10 * * ?}")
    public void remind() {
        LocalDate today = LocalDate.now();
        try {
            int pushed = rentOrderService.remindDueOrders(today);
            log.info("房租提醒任务完成: date={}, 推送={} 条（提前 {} 天）",
                    today, pushed, RentConstant.REMIND_BEFORE_DAYS);
        } catch (Exception e) {
            log.error("房租提醒任务执行失败: date={}", today, e);
        }
    }
}
