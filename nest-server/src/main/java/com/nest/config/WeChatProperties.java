package com.nest.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 微信小程序登录配置（appid/secret 仅服务端；mock 仅本地开发，生产严禁开启）。 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "nest.wechat")
public class WeChatProperties {

    private String appid;

    private String secret;

    /** 仅本地开发：伪造 openid 绕过微信，生产严禁开启 */
    private boolean mockEnabled;

    /** MOCK 固定 openid，避免每次登录账号漂移（留空则按 code 派生建新号） */
    private String mockOpenid;

    /** 启动校验：非 mock 模式缺 appid/secret 则启动失败。 */
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
