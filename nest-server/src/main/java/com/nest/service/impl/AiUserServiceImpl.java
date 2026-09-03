package com.nest.service.impl;

import com.nest.AI.config.NestAiAgent.TenantAiAssistant;
import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.service.AiUserService;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.TokenStream;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * AI 找房服务实现 —— SSE 流式输出。
 *
 * <p>复用外卖验证过的 {@code AsyncContext + PrintWriter + writeSSE} 模式。
 * Nest 是真 Function Calling（AiServices），工具调用由 LangChain4j 内部处理，
 * {@code onPartialResponse} 只在最终文字生成时触发，因此流式输出只需把
 * 每个文字块写成 SSE 事件。</p>
 */
@Slf4j
@Service
public class AiUserServiceImpl implements AiUserService {

    private final TenantAiAssistant tenantAiAssistant;

    public AiUserServiceImpl(TenantAiAssistant tenantAiAssistant) {
        this.tenantAiAssistant = tenantAiAssistant;
    }

    /**
     * 流式输出 AI 找房对话
     * @param message      用户消息
     * @param asyncContext 异步上下文（用于拿到响应流写 SSE）
     */
    @Override
    public void streamChat(String message, AsyncContext asyncContext) {
        HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
        //设置响应的基本信息，确保浏览器正确解析 SSE 事件
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        // 从 BaseContext 获取当前租客 ID
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            // 租客未登录，返回错误信息
            writeSSEAndClose(response, "data:" + MessageConstant.AI_NOT_LOGIN + "\n\n", "data: [DONE]\n\n");
            // 异步上下文完成，关闭响应流
            asyncContext.complete();
            return;
        }

        if (message == null || message.isBlank()) {
            writeSSEAndClose(response, "data: 请告诉我你想找什么样的房子\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }

        log.info("AI 找房对话 [tenantId={}]: {}", tenantId,
                message.length() > 50 ? message.substring(0, 50) + "..." : message);

        try {
            PrintWriter writer = response.getWriter();

            // AiServices 真 Function Calling：内部自动决策 + 调用工具
            TokenStream tokenStream = tenantAiAssistant.chat(message);
            tokenStream.onPartialResponse(token -> writeSSE(writer, token))
                    .onCompleteResponse(resp -> {
                        writeSSE(writer, "[DONE]");
                        writer.flush();
                        writer.close();
                        log.info("AI 对话完成 [tenantId={}]", tenantId);
                        asyncContext.complete();
                    })
                    .onError(error -> {
                        log.error("AI 对话失败 [tenantId={}]", tenantId, error);
                        try {
                            writeSSE(writer, "[ERROR] " + MessageConstant.AI_SERVICE_ERROR);
                            writeSSE(writer, "[DONE]");
                            writer.flush();
                            writer.close();
                        } catch (Exception ignored) {
                        }
                        asyncContext.complete();
                    })
                    .start();

        } catch (IOException e) {
            log.error("AI 流式响应 IO 异常", e);
            asyncContext.complete();
        }
    }

    /** 写 SSE 事件：内容多行自动拆成多个 data: 行，空行结束事件 */
    private void writeSSE(PrintWriter writer, String data) {
        for (String line : data.split("\n", -1)) {
            writer.write("data:" + line + "\n");
        }
        writer.write("\n");
        writer.flush();
    }

    /** 写若干 SSE 行后关闭响应 */
    private void writeSSEAndClose(HttpServletResponse response, String... lines) {
        try {
            PrintWriter w = response.getWriter();
            for (String line : lines) w.write(line);
            w.flush();
            w.close();
        } catch (IOException ignored) {
        }
    }
}
