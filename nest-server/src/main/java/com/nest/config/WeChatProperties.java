package com.nest.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录配置。
 *
 * <p>{@code appid}/{@code secret} 仅由服务端持有，<b>绝不进 git、绝不进小程序包</b>。
 * {@code mockEnabled=true} 时返回伪造 openid（仅本地开发用，<b>生产严禁开启</b>）。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "nest.wechat")
public class WeChatProperties {

    /** 小程序 AppID */
    private String appid;

    /** 小程序 AppSecret */
    private String secret;

    /** 仅本地开发：用伪造 openid 绕过微信调用，生产严禁开启 */
    private boolean mockEnabled;

    /**
     * 仅本地开发：MOCK 模式下使用的<b>固定 openid</b>。
     *
     * <p>小程序 {@code uni.login()} 每次拿到的 code 都是一次性的，若按 code 派生 openid，
     * 则每次登录都会落到一个新租客（账号漂移、历史消息"消失"）。配置该值后，
     * MOCK 模式下所有登录都映射到同一个租客，本地反复调试是同一个账号。
     *
     * <p>留空则退化为旧行为（按 code 派生，每次登录建新号）。
     */
    private String mockOpenid;

    /**
     * 启动即校验：非 mock 模式下缺凭证直接起不来，别等用户登录时才发现。
     */
    @PostConstruct
    public void validate() {
        if (mockEnabled) {
            log.warn("⚠️ 微信登录处于 MOCK 模式，openid 为伪造值，生产环境严禁开启"
                    + "（固定 openid={}）", mockOpenid == null || mockOpenid.isBlank() ? "未配置，按 code 派生" : mockOpenid);
            return;
        }
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "微信登录未配置：请设置 NEST_WECHAT_APPID / NEST_WECHAT_SECRET，"
                            + "或本地开发时开启 nest.wechat.mock-enabled=true");
        }
    }
}
