package com.nest.ai.service.impl;

import com.nest.ai.config.NestAiAgent.TenantAiAssistant;
import com.nest.ai.service.AiUserService;
import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import dev.langchain4j.service.TokenStream;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;

/** AI 找房服务实现 —— SSE 流式输出。 */
@Slf4j
@Service
public class AiUserServiceImpl implements AiUserService {

    private final TenantAiAssistant tenantAiAssistant;

    public AiUserServiceImpl(TenantAiAssistant tenantAiAssistant) {
        this.tenantAiAssistant = tenantAiAssistant;
    }

    @Override
    public void streamChat(String message, AsyncContext asyncContext) {
        HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            writeSSEAndClose(response, "data:" + MessageConstant.AI_NOT_LOGIN + "\n\n", "data: [DONE]\n\n");
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

    private void writeSSE(PrintWriter writer, String data) {
        for (String line : data.split("\n", -1)) {
            writer.write("data:" + line + "\n");
        }
        writer.write("\n");
        writer.flush();
    }

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
