package com.nest.ai.service.impl;

import com.nest.ai.config.NestAiAgent.TenantAiAssistant;
import com.nest.ai.service.AiUserService;
import com.nest.ai.support.AiInputGuard;
import com.nest.ai.support.AiTraceCollector;
import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.exception.NotLoginException;
import com.nest.service.AiQuotaService;
import com.nest.service.AiTraceStore;
import com.nest.vo.ai.AiMessageVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class AiUserServiceImpl implements AiUserService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_AI = "ai";

    private final TenantAiAssistant tenantAiAssistant;

    private final ChatMemoryStore chatMemoryStore;

    private final Map<Long, Semaphore> tenantChatLocks = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .<Long, Semaphore>build()
            .asMap();

    @Autowired(required = false)
    private AiTraceStore aiTraceStore;

    @Autowired(required = false)
    private AiQuotaService aiQuotaService;

    @Value("${nest.ai.quota.daily-user-requests:100}")
    private int dailyUserQuota;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public AiUserServiceImpl(TenantAiAssistant tenantAiAssistant, ChatMemoryStore chatMemoryStore) {
        this.tenantAiAssistant = tenantAiAssistant;
        this.chatMemoryStore = chatMemoryStore;
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

        String safeMessage = AiInputGuard.sanitize(message);
        if (safeMessage.isBlank()) {
            writeSSEAndClose(response, "data: 请告诉我你想找什么样的房子\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }
        if (AiInputGuard.looksLikePromptInjection(safeMessage)) {
            log.warn("AI 输入命中提示注入模式，已拒绝 [tenantId={}]", tenantId);
            writeSSEAndClose(response, "data:" + MessageConstant.AI_INPUT_INVALID + "\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }
        if (aiQuotaService != null && !aiQuotaService.tryAcquire(tenantId, dailyUserQuota)) {
            writeSSEAndClose(response, "data:" + MessageConstant.AI_QUOTA_EXCEEDED + "\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }

        Semaphore lock = tenantChatLocks.computeIfAbsent(tenantId, k -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            writeSSEAndClose(response, "data:" + MessageConstant.AI_CHAT_IN_PROGRESS + "\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }

        String traceId = UUID.randomUUID().toString();
        response.setHeader("X-Trace-Id", traceId);
        AiTraceCollector trace = new AiTraceCollector(traceId, tenantId, "user", safeMessage);
        long startNanos = System.nanoTime();

        log.info("AI 找房对话 [tenantId={}, traceId={}]: {}", tenantId, traceId,
                safeMessage.length() > 50 ? safeMessage.substring(0, 50) + "..." : safeMessage);

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            log.error("AI 对话获取输出流失败 [tenantId={}]", tenantId, e);
            lock.release();
            asyncContext.complete();
            return;
        }

        AtomicBoolean finished = new AtomicBoolean();
        asyncContext.addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
                trace.onError(new IllegalStateException("async timeout"));
                finish(finished, writer, asyncContext, lock, true, trace, startNanos);
            }

            @Override
            public void onError(AsyncEvent event) {
                trace.onError(new IllegalStateException("async error"));
                finish(finished, writer, asyncContext, lock, true, trace, startNanos);
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }
        });

        try {
            TokenStream tokenStream = tenantAiAssistant.chat(tenantId, safeMessage);
            tokenStream.onPartialResponse(token -> {
                        if (!finished.get()) {
                            writeSSE(writer, token);
                        }
                    })
                    .onToolExecuted(trace::onToolExecuted)
                    .onCompleteResponse(resp -> {
                        trace.onResponse(resp);
                        finish(finished, writer, asyncContext, lock, false, trace, startNanos);
                    })
                    .onError(error -> {
                        log.error("AI 对话失败 [tenantId={}]", tenantId, error);
                        trace.onError(error);
                        finish(finished, writer, asyncContext, lock, true, trace, startNanos);
                    })
                    .start();
        } catch (Exception e) {
            log.error("AI 对话异常 [tenantId={}]", tenantId, e);
            trace.onError(e);
            finish(finished, writer, asyncContext, lock, true, trace, startNanos);
        }
    }

    private void finish(AtomicBoolean finished, PrintWriter writer, AsyncContext asyncContext,
                        Semaphore lock, boolean asError, AiTraceCollector trace, long startNanos) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            if (asError) {
                writeSSE(writer, "[ERROR] " + MessageConstant.AI_SERVICE_ERROR);
            }
            writeSSE(writer, "[DONE]");
            writer.close();
        } catch (Exception ignored) {
        }
        try {
            asyncContext.complete();
        } catch (Exception ignored) {
        }
        persistTrace(trace, startNanos, !asError);
        recordMetrics(!asError, System.nanoTime() - startNanos);
        lock.release();
    }

    private void recordMetrics(boolean success, long latencyNanos) {
        if (meterRegistry == null) {
            return;
        }
        try {
            meterRegistry.counter("nest.ai.requests", "channel", "user", "outcome", success ? "success" : "error")
                    .increment();
            meterRegistry.timer("nest.ai.latency", "channel", "user")
                    .record(java.time.Duration.ofNanos(latencyNanos));
        } catch (Exception e) {
            log.debug("AI 指标记录失败: {}", e.getMessage());
        }
    }

    private void persistTrace(AiTraceCollector trace, long startNanos, boolean success) {
        if (trace == null || aiTraceStore == null) {
            return;
        }
        try {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            aiTraceStore.save(trace.toRecord(success, latencyMs));
        } catch (Exception e) {
            log.warn("AI 轨迹提交失败: {}", e.getMessage());
        }
    }

    @Override
    public List<AiMessageVO> getHistory() {
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            return new ArrayList<>();
        }
        try {
            List<AiMessageVO> history = new ArrayList<>();
            for (ChatMessage message : chatMemoryStore.getMessages(tenantId)) {
                AiMessageVO vo = toVO(message);
                if (vo != null) {
                    history.add(vo);
                }
            }
            return history;
        } catch (Exception e) {

            log.warn("读取 AI 对话历史失败（降级为空）[tenantId={}]: {}", tenantId, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void clearMemory() {
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            throw new NotLoginException(MessageConstant.NOT_LOGIN);
        }
        chatMemoryStore.deleteMessages(tenantId);
        log.info("AI 对话记忆已清空 [tenantId={}]", tenantId);
    }

    private AiMessageVO toVO(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.hasSingleText()
                    ? new AiMessageVO(ROLE_USER, userMessage.singleText())
                    : null;
        }
        if (message instanceof AiMessage aiMessage) {

            String text = aiMessage.text();
            return (text != null && !text.isBlank()) ? new AiMessageVO(ROLE_AI, text) : null;
        }
        return null;
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
