package com.nest.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nest.config.WeChatProperties;
import com.nest.dto.WxSessionResponse;
import com.nest.exception.BusinessException;
import com.nest.service.WxAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * 微信登录凭证换取服务实现。
 */
@Slf4j
@Service
public class WxAuthServiceImpl implements WxAuthService {

    private static final String WX_HOST = "api.weixin.qq.com";
    private static final String JSCODE2SESSION_PATH = "/sns/jscode2session";

    /** 异常日志中原始响应的最大长度，避免刷屏。 */
    private static final int RAW_LOG_LIMIT = 500;

    private final WeChatProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WxAuthServiceImpl(WeChatProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
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

        // 微信 jscode2session 返回的 Content-Type 是 text/plain，body 内容却是 JSON。
        // 若直接 .body(WxSessionResponse.class)，RestClient 会因找不到 text/plain → 对象的
        // 消息转换器而抛 UnknownContentTypeException，连微信真正的 errcode 都读不到。
        // 因此先按 String 取回原文，再自行反序列化。
        String raw;
        try {
            raw = restClient.get()
                    .uri(b -> b.scheme("https")
                            .host(WX_HOST)
                            .path(JSCODE2SESSION_PATH)
                            .queryParam("appid", props.getAppid())
                            .queryParam("secret", props.getSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // 网络 / HTTP 层失败。code 一次性，重试无意义，只能让前端重新 wx.login()
            log.error("调用微信 jscode2session 失败（网络或 HTTP 层）", e);
            throw new BusinessException("微信服务暂时不可用，请稍后再试");
        }

        if (raw == null || raw.isBlank()) {
            log.error("微信 jscode2session 返回空响应");
            throw new BusinessException("微信服务暂时不可用，请稍后再试");
        }

        WxSessionResponse resp;
        try {
            resp = objectMapper.readValue(raw, WxSessionResponse.class);
        } catch (JsonProcessingException e) {
            // 收到的不是微信的 JSON（例如网关 / 代理返回的错误页）；原文留档便于排查
            log.error("微信 jscode2session 响应非 JSON, raw={}", abbreviate(raw), e);
            throw new BusinessException("微信服务暂时不可用，请稍后再试");
        }

        if (resp == null) {
            log.error("微信 jscode2session 响应解析结果为 null, raw={}", abbreviate(raw));
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

    /** 截断过长的原始响应，避免异常日志刷屏。 */
    private String abbreviate(String raw) {
        return raw.length() <= RAW_LOG_LIMIT ? raw : raw.substring(0, RAW_LOG_LIMIT) + "...(truncated)";
    }
}
