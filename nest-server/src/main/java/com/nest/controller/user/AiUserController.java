package com.nest.controller.user;

import com.nest.ai.service.AiUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租客端 AI 找房接口 —— SSE 流式。
 */
@Slf4j
@RestController
@RequestMapping("/user/ai")
@RequiredArgsConstructor
@Tag(name = "租客端-AI找房", description = "自然语言找房，SSE 流式返回")
public class AiUserController {

    private final AiUserService aiUserService;

    /**
     * 流式 AI 找房对话。
     * 返回 SSE（text/event-stream），每段文字一个 data: 事件，结束发 [DONE]。
     */
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    @Operation(summary = "AI 流式找房", description = "租客输入自然语言，AI 通过工具查真实房源并逐字返回")
    public void streamChat(@RequestBody(required = false) Map<String, String> body,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        String message = body != null ? body.get("message") : null;

        // 开启异步处理（SSE 长连接需要，不能占用 Servlet 线程）
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(120000); // 2 分钟超时

        aiUserService.streamChat(message, asyncContext);
    }
}
