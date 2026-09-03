package com.nest.service;

import jakarta.servlet.AsyncContext;

/**
 * AI 找房服务接口。
 */
public interface AiUserService {

    /**
     * 流式 AI 找房对话（SSE）。
     *
     * @param message      用户消息
     * @param asyncContext 异步上下文（用于拿到响应流写 SSE）
     */
    void streamChat(String message, AsyncContext asyncContext);
}
