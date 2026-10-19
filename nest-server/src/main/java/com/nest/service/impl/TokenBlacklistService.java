package com.nest.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "nest:auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
        } catch (Exception e) {

            log.warn("JWT 吊销名单查询失败，按未吊销处理: {}", e.getMessage());
            return false;
        }
    }

    public void revoke(String secret, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = JwtUtil.parseToken(secret, token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank() || claims.getExpiration() == null) {
                return;
            }
            long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMillis <= 0) {
                return;
            }
            redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
            log.info("JWT 已吊销: jti={}, ttlMs={}", jti, ttlMillis);
        } catch (Exception e) {
            log.warn("JWT 吊销失败（token 可能已过期/无效）: {}", e.getMessage());
        }
    }

    public void revokeAdmin(String token) {
        revoke(JwtConstant.adminSecretKey(), token);
    }

    public void revokeUser(String token) {
        revoke(JwtConstant.userSecretKey(), token);
    }
}
