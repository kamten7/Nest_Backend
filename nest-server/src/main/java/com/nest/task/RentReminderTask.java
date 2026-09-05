package com.nest.task;

import com.alibaba.fastjson2.JSONObject;
import com.nest.constant.JwtConstant;
import com.nest.constant.RentConstant;
import com.nest.entity.RentOrder;
import com.nest.entity.RentReminderLog;
import com.nest.mapper.RentOrderMapper;
import com.nest.mapper.RentReminderLogMapper;
import com.nest.utils.RentPeriodUtil;
import com.nest.websocket.ChatWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 房租到期提醒定时任务。
 *
 * 每天扫描「租房中」订单：若今天 ≥ nextDuePeriod 起始日 − 3 天，推送提醒并写日志去重。
 * 提前支付过的订单因 nextDuePeriod 前移，提醒会自动顺延。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentReminderTask {

    private final RentOrderMapper rentOrderMapper;
    private final RentReminderLogMapper rentReminderLogMapper;

    /** 每天 10:00 执行 */
    @Scheduled(cron = "0 0 10 * * ?")
    public void remindRent() {
        List<RentOrder> orders = rentOrderMapper.selectByStatus(RentConstant.ORDER_RENTING);
        LocalDate today = LocalDate.now();

        for (RentOrder order : orders) {
            // 1. 计算提醒日 = nextDuePeriod 起始日 - 3 天
            LocalDate remindDate = RentPeriodUtil.startOf(order.getNextDuePeriod())
                    .minusDays(RentConstant.REMINDER_DAYS);
            // 2. 未到提醒日则跳过
            if (today.isBefore(remindDate)) {
                continue;
            }
            // 3. 同一订单、同一周期、同一天只提醒一次
            int cnt = rentReminderLogMapper.countByOrderAndPeriod(
                    order.getId(), order.getNextDuePeriod(), today);
            if (cnt > 0) {
                continue;
            }
            // 4. 推送 WS 提醒并写日志去重
            JSONObject msg = new JSONObject();
            msg.put("type", "rent_reminder");
            msg.put("orderId", order.getId());
            msg.put("title", "房租到期提醒");
            msg.put("content", "您的租房订单「" + order.getOrderNo() + "」下期房租请及时缴纳，可在「我的-租房订单」中操作。");
            ChatWebSocketServer.sendToUser(JwtConstant.TYPE_TENANT, order.getTenantId(), msg.toJSONString());

            rentReminderLogMapper.insert(RentReminderLog.builder()
                    .orderId(order.getId())
                    .remindPeriod(order.getNextDuePeriod())
                    .remindDate(today)
                    .build());
            log.info("房租到期提醒: orderId={}, tenantId={}, period={}",
                    order.getId(), order.getTenantId(), order.getNextDuePeriod());
        }
    }
}
