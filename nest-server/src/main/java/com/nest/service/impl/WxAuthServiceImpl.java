package com.nest.service.impl;

import com.nest.config.WeChatProperties;
import com.nest.dto.WxSessionResponse;
import com.nest.exception.BusinessException;
import com.nest.service.WxAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 微信登录凭证换取服务实现。
 */
@Slf4j
@Service
public class WxAuthServiceImpl implements WxAuthService {

    private static final String WX_HOST = "api.weixin.qq.com";
    private static final String JSCODE2SESSION_PATH = "/sns/jscode2session";

    private final WeChatProperties props;
    private final RestClient restClient;

    public WxAuthServiceImpl(WeChatProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String code2Openid(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("缺少微信登录凭证");
        }

        WxSessionResponse resp;
        try {
            resp = restClient.get()
                    .uri(b -> b.scheme("https")
                            .host(WX_HOST)
                            .path(JSCODE2SESSION_PATH)
                            .queryParam("appid", props.getAppid())
                            .queryParam("secret", props.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WxSessionResponse.class);
        } catch (Exception e) {
            // code 一次性：失败重试也没用，直接让前端重新 wx.login()
            log.error("调用微信 jscode2session 异常", e);
            throw new BusinessException("微信服务暂时不可用，请稍后再试");
        }

        if (resp == null) {
            throw new BusinessException("微信服务暂时不可用，请稍后再试");
        }

        // ★ 成功响应可能不带 errcode 字段，必须判空
        Integer errcode = resp.getErrcode();
        if (errcode != null && errcode != 0) {
            log.warn("微信登录失败: errcode={}, errmsg={}", errcode, resp.getErrmsg());
            throw new BusinessException(mapError(errcode));
        }

        String openid = resp.getOpenid();
        if (openid == null || openid.isBlank()) {
            throw new BusinessException("微信登录失败，请稍后再试");
        }
        log.info("微信 code 换取 openid 成功: openid={}", mask(openid));
        return openid;
    }

    /** errcode → 用户可读提示。默认分支不把 errmsg 原样透给用户。 */
    private String mapError(int errcode) {
        return switch (errcode) {
            case 40029 -> "登录已过期，请重新登录";
            case 45011 -> "操作过于频繁，请稍后再试";
            case 40226 -> "账号存在风险，暂无法登录";
            case -1 -> "微信服务繁忙，请稍后再试";
            default -> "微信登录失败（" + errcode + "）";
        };
    }

    /** openid 日志脱敏：只保留前 6 后 4。 */
    private String mask(String openid) {
        if (openid == null || openid.length() <= 10) {
            return "***";
        }
        return openid.substring(0, 6) + "****" + openid.substring(openid.length() - 4);
    }
}
