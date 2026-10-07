package com.nest.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nest.common.BaseContext;
import com.nest.entity.House;
import com.nest.exception.BusinessException;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.HouseTagMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.minio.service.MinioService;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.service.impl.HouseServiceImpl;
import com.nest.vo.HouseVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class HouseCacheTest {

    private static final Long HOUSE_ID = 10L;

    @Mock private HouseMapper houseMapper;
    @Mock private HouseImageMapper houseImageMapper;
    @Mock private HouseTagMapper houseTagMapper;
    @Mock
    private LandlordMapper landlordMapper;
    @Mock private MinioService minioService;
    @Mock private RentOrderMapper rentOrderMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HouseServiceImpl houseService;

    @BeforeEach
    void setUp() {
        houseService = new HouseServiceImpl(
                stringRedisTemplate,
                objectMapper,
                houseMapper,
                houseImageMapper,
                houseTagMapper,
                landlordMapper,
                minioService,
                rentOrderMapper);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentType("tenant");
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private House onShelfHouse() {
        House h = new House();
        h.setId(HOUSE_ID);
        h.setLandlordId(1L);
        h.setStatus(1);
        h.setTitle("银帆花园二居室");
        return h;
    }

    @Test
    @DisplayName("缓存命中：不查数据库直接返回")
    void cacheHit_skipsDb() throws Exception {
        HouseVO vo = new HouseVO();
        vo.setId(HOUSE_ID);
        vo.setTitle("银帆花园二居室");
        when(valueOps.get("cache:house:detail:" + HOUSE_ID))
                .thenReturn(objectMapper.writeValueAsString(vo));

        HouseVO result = houseService.detail(HOUSE_ID);

        assertThat(result.getTitle()).isEqualTo("银帆花园二居室");
        verify(houseMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("穿透防护：DB 无数据 → 写入空值标记并抛 NOT_FOUND")
    void nullValueCache_preventsPenetration() {
        when(valueOps.get(any())).thenReturn(null);
        when(houseMapper.selectById(HOUSE_ID)).thenReturn(null);
        when(stringRedisTemplate.opsForValue()
                .setIfAbsent(startsWith("cache:house:lock:"), any(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> houseService.detail(HOUSE_ID))
                .isInstanceOf(BusinessException.class);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("cache:house:detail:" + HOUSE_ID), valueCaptor.capture(),
                eq(2L), eq(TimeUnit.MINUTES));
        assertThat(valueCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("击穿防护：没抢到重建锁 → 自旋重读缓存命中，不查 DB")
    void mutexLock_preventsCacheStampede() throws Exception {
        HouseVO vo = new HouseVO();
        vo.setId(HOUSE_ID);
        vo.setTitle("银帆花园二居室");
        when(valueOps.get("cache:house:detail:" + HOUSE_ID))
                .thenReturn(null)                                    // 第一次读：未命中
                .thenReturn(objectMapper.writeValueAsString(vo));    // 重试时：已被别的线程回填
        when(stringRedisTemplate.opsForValue()
                .setIfAbsent(startsWith("cache:house:lock:"), any(), any(Duration.class))).thenReturn(false);

        HouseVO result = houseService.detail(HOUSE_ID);

        assertThat(result.getTitle()).isEqualTo("银帆花园二居室");
        verify(houseMapper, never()).selectById(any());
    }
}