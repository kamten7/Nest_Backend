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
     * 启动即校验：非 mock 模式下缺凭证直接起不来，别等用户登录时才发现。
     */
    @PostConstruct
    public void validate() {
        if (mockEnabled) {
            log.warn("⚠️ 微信登录处于 MOCK 模式，openid 为伪造值，生产环境严禁开启");
            return;
        }
        if (appid == null || appid.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "微信登录未配置：请设置 NEST_WECHAT_APPID / NEST_WECHAT_SECRET，"
                            + "或本地开发时开启 nest.wechat.mock-enabled=true");
        }
    }
}
