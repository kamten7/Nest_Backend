package com.nest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/** WebSocket 配置 —— 自动注册 @ServerEndpoint 端点。 */
@Slf4j
@Configuration
public class WebSocketConfiguration {

    /** 注册 JSR-356 ServerEndpointExporter（@ServerEndpoint 需显式注册）。 */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        log.info("WebSocket 配置加载");
        return new ServerEndpointExporter();
    }
}
