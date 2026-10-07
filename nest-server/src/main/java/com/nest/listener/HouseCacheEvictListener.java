package com.nest.listener;

import com.nest.order.event.HouseStatusChangedEvent;
import com.nest.service.HouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HouseCacheEvictListener {

    private final HouseService houseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHouseStatusChanged(HouseStatusChangedEvent event) {
        try {
            houseService.evictCache(event.houseId());
            log.info("房源状态变更事件：已失效详情缓存 houseId={}", event.houseId());
        } catch (Exception e) {
            log.error("房源状态变更事件：缓存失效失败 houseId={}", event.houseId(), e);
        }
    }
}
