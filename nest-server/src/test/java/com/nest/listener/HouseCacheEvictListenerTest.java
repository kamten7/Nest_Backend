package com.nest.listener;

import com.nest.order.event.HouseStatusChangedEvent;
import com.nest.service.HouseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseCacheEvictListenerTest {

    private static final Long HOUSE_ID = 10L;

    @Mock
    private HouseService houseService;

    @InjectMocks
    private HouseCacheEvictListener listener;

    @Test
    @DisplayName("收到房源状态变更事件 → 失效详情缓存")
    void evictsCacheOnEvent() {
        listener.onHouseStatusChanged(new HouseStatusChangedEvent(HOUSE_ID));

        verify(houseService).evictCache(HOUSE_ID);
    }

    @Test
    @DisplayName("缓存失效抛异常 → 不向上传播（afterCommit 阶段异常无人接管）")
    void swallowsEvictFailure() {
        doThrow(new RuntimeException("redis down")).when(houseService).evictCache(HOUSE_ID);

        listener.onHouseStatusChanged(new HouseStatusChangedEvent(HOUSE_ID));

        verify(houseService).evictCache(HOUSE_ID);
    }
}
