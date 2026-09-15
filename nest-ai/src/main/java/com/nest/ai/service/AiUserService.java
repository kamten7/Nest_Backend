package com.nest.ai.service;

import jakarta.servlet.AsyncContext;

/** AI 找房服务接口。 */
public interface AiUserService {

    void streamChat(String message, AsyncContext asyncContext);
}
