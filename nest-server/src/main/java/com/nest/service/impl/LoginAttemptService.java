package com.nest.service.impl;

import com.nest.constant.MessageConstant;
import com.nest.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String PREFIX = "nest:login:";
    private static final String FAIL = ":fail:";
    private static final String LOCK = ":lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${nest.security.login.max-failures:5}")
    private int maxFailures;

    @Value("${nest.security.login.window-seconds:900}")
    private long windowSeconds;

    @Value("${nest.security.login.lock-seconds:900}")
    private long lockSeconds;

    public void checkAllowed(String channel, String account, String ip) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(accountLockKey(channel, account)))
                    || Boolean.TRUE.equals(redisTemplate.hasKey(ipLockKey(channel, ip)))) {
                throw new BusinessException(MessageConstant.LOGIN_TOO_MANY_ATTEMPTS);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录限流检查失败，放行本次请求: {}", e.getMessage());
        }
    }

    public void onFailure(String channel, String account, String ip) {
        try {
            long accountCount = increment(accountFailKey(channel, account));
            if (accountCount >= maxFailures) {
                redisTemplate.opsForValue().set(accountLockKey(channel, account), "1", Duration.ofSeconds(lockSeconds));
                log.warn("账号登录失败次数达到阈值，已锁定: channel={}, account={}, count={}", channel, account, accountCount);
            }
            long ipCount = increment(ipFailKey(channel, ip));
            if (ipCount >= (long) maxFailures * 3) {
                redisTemplate.opsForValue().set(ipLockKey(channel, ip), "1", Duration.ofSeconds(lockSeconds));
                log.warn("IP 登录失败次数达到阈值，已锁定: channel={}, ip={}, count={}", channel, ip, ipCount);
            }
        } catch (Exception e) {
            log.warn("登录失败计数异常: {}", e.getMessage());
        }
    }

    public void onSuccess(String channel, String account) {
        try {
            redisTemplate.delete(accountFailKey(channel, account));
            redisTemplate.delete(accountLockKey(channel, account));
        } catch (Exception e) {
            log.warn("清理登录失败计数异常: {}", e.getMessage());
        }
    }

    public static String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            String forwarded = attributes.getRequest().getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return attributes.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private long increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
        return count == null ? 0 : count;
    }

    private static String accountFailKey(String channel, String account) {
        return PREFIX + channel + FAIL + account;
    }

    private static String accountLockKey(String channel, String account) {
        return PREFIX + channel + LOCK + account;
    }

    private static String ipFailKey(String channel, String ip) {
        return PREFIX + channel + FAIL + "ip:" + ip;
    }

    private static String ipLockKey(String channel, String ip) {
        return PREFIX + channel + LOCK + "ip:" + ip;
    }
}
