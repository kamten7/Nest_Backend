package com.nest.config;

import com.nest.constant.JwtConstant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** JWT 密钥启动注入：把配置值写入 JwtConstant，并做 fail-fast 校验。 */
@Slf4j
@Configuration
public class JwtSecretInitializer {

    @Value("${nest.jwt.admin-secret-key:}")
    private String adminSecret;

    @Value("${nest.jwt.user-secret-key:}")
    private String userSecret;

    @PostConstruct
    public void init() {
        if (isBlank(adminSecret) || isBlank(userSecret)) {
            throw new IllegalStateException(
                    "JWT 密钥未配置：请设置环境变量 NEST_JWT_ADMIN_SECRET / NEST_JWT_USER_SECRET"
                            + "（生产环境必须使用高强度随机值，禁止使用示例值）");
        }
        if (adminSecret.equals(userSecret)) {
            throw new IllegalStateException("房东端与租客端 JWT 密钥不能相同（双通道隔离要求）");
        }
        JwtConstant.initSecrets(adminSecret, userSecret);
        log.info("JWT 密钥初始化完成（admin 长度={}, user 长度={}）", adminSecret.length(), userSecret.length());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}