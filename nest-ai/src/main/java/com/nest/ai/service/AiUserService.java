package com.nest.ai.service;

import jakarta.servlet.AsyncContext;

/** AI 找房服务接口。 */
public interface AiUserService {

    /** 流式 AI 找房对话（SSE）。 */
    void streamChat(String message, AsyncContext asyncContext);
}
