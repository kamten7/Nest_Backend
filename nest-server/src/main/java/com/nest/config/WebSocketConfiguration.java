package com.nest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 关于 WebSocket 开发的规范：
 * 1. 所有 WebSocket 端点必须使用 {@code @ServerEndpoint} 注解。
 * 2. 所有 WebSocket 端点的路径必须是唯一的。
 *
 * WebSocket 配置 —— 自动注册 {@code @ServerEndpoint} 注解的端点。
 */
@Slf4j
@Configuration
public class WebSocketConfiguration {

    // JSR-356 的 @ServerEndpoint 不是 Spring 自动扫描的，必须通过 ServerEndpointExporter 显式注册。
    //JSR-356 就是 Java 官方为 WebSocket 制定的标准编程接口（API）。
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        log.info("WebSocket 配置加载");
        return new ServerEndpointExporter();
    }
}
