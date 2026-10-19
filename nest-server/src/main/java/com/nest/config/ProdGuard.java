package com.nest.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("prod")
public class ProdGuard {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${nest.jwt.admin-secret-key:}")
    private String adminSecret;

    @Value("${nest.jwt.user-secret-key:}")
    private String userSecret;

    @Value("${nest.auth.phone-login-enabled:false}")
    private boolean phoneLoginEnabled;

    @Value("${nest.wallet.simulate-recharge-enabled:false}")
    private boolean simulateRechargeEnabled;

    @Value("${nest.wechat.mock-enabled:false}")
    private boolean wechatMockEnabled;

    @Value("${nest.cors.allowed-origins:}")
    private String allowedOrigins;

    @PostConstruct
    public void check() {
        require(!phoneLoginEnabled,
                "nest.auth.phone-login-enabled 必须为 false：手机号直登会绕过短信验证码");
        require(!simulateRechargeEnabled,
                "nest.wallet.simulate-recharge-enabled 必须为 false：模拟充值会让人无限加余额");
        require(!wechatMockEnabled,
                "nest.wechat.mock-enabled 必须为 false：mock 登录会伪造 openid");
        require(adminSecret != null && adminSecret.length() >= MIN_SECRET_LENGTH,
                "nest.jwt.admin-secret-key 长度必须 >= " + MIN_SECRET_LENGTH);
        require(userSecret != null && userSecret.length() >= MIN_SECRET_LENGTH,
                "nest.jwt.user-secret-key 长度必须 >= " + MIN_SECRET_LENGTH);
        require(adminSecret != null && !adminSecret.equals(userSecret),
                "房东端与租客端 JWT 密钥不能相同");
        require(allowedOrigins != null && !allowedOrigins.isBlank(),
                "nest.cors.allowed-origins 必须显式配置具体来源");
        require(!allowedOrigins.contains("*"),
                "nest.cors.allowed-origins 在生产环境禁止使用 *");

        log.info("ProdGuard 校验通过：mock/模拟充值/手机号直登已关闭，CORS 来源={}", allowedOrigins);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("[ProdGuard] 生产配置不安全：" + message);
        }
    }
}
