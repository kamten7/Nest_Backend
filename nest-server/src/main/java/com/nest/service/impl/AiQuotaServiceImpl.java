package com.nest.service.impl;

import com.nest.service.AiQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaServiceImpl implements AiQuotaService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(Long tenantId, int dailyLimit) {
        if (tenantId == null || dailyLimit <= 0) {
            return true;
        }
        String key = "nest:ai:quota:" + tenantId + ":" + LocalDate.now();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofDays(2));
            }
            boolean allowed = count == null || count <= dailyLimit;
            if (!allowed) {
                log.warn("AI 每日配额已用完: tenantId={}, count={}, limit={}", tenantId, count, dailyLimit);
            }
            return allowed;
        } catch (Exception e) {

            log.warn("AI 配额检查失败，放行本次请求: tenantId={}, {}", tenantId, e.getMessage());
            return true;
        }
    }
}
