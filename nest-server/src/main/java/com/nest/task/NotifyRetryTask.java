package com.nest.task;

import com.nest.chat.push.PushService;
import com.nest.entity.NotifyTask;
import com.nest.mapper.NotifyTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRetryTask {

    private static final int BATCH_LIMIT = 50;

    private final NotifyTaskMapper notifyTaskMapper;
    private final PushService pushService;

    @Scheduled(fixedDelay = 30000)
    public void retryPending() {
        List<NotifyTask> pending;
        try {
            pending = notifyTaskMapper.selectPending(BATCH_LIMIT);
        } catch (Exception e) {
            log.error("通知补偿扫描失败", e);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        int sent = 0;
        for (NotifyTask task : pending) {
            try {
                pushService.pushNoticeDirect(task.getUserType(), task.getUserId(),
                        task.getType(), task.getTitle(), task.getContent());
                notifyTaskMapper.markSent(task.getId());
                sent++;
            } catch (Exception e) {
                log.warn("通知补偿重试失败: id={}, retryCount={}", task.getId(), task.getRetryCount(), e);
                notifyTaskMapper.incrementRetry(task.getId());
            }
        }
        log.info("通知补偿任务完成: 待处理={} 条, 补发成功={} 条", pending.size(), sent);
    }
}
