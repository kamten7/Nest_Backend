package com.nest.ai.service.impl;

import com.nest.ai.config.NestAiAgent.TenantAiAssistant;
import com.nest.ai.service.AiUserService;
import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.exception.NotLoginException;
import com.nest.vo.AiMessageVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** AI 找房服务实现 —— SSE 流式输出。 */
@Slf4j
@Service
public class AiUserServiceImpl implements AiUserService {

    /** 对话历史里的角色标识，与小程序气泡的 role 对齐 */
    private static final String ROLE_USER = "user";
    private static final String ROLE_AI = "ai";

    private final TenantAiAssistant tenantAiAssistant;

    /** LangChain4j 的记忆存储抽象；运行时由 nest-server 的 RedisChatMemoryStore 实现 */
    private final ChatMemoryStore chatMemoryStore;

    /** 同租户对话串行信号量：记忆读改写非原子，并发多路流会互相覆盖；tryAcquire 失败直接提示稍后再试。
     *  用 Semaphore 而非 ReentrantLock：终态收口(finish)由模型回调线程或容器超时线程执行，
     *  与获取许可的请求线程不是同一个，ReentrantLock 非持有者 unlock 会抛 IllegalMonitorStateException。
     */
    private final Map<Long, Semaphore> tenantChatLocks = new ConcurrentHashMap<>();

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

        if (message == null || message.isBlank()) {
            writeSSEAndClose(response, "data: 请告诉我你想找什么样的房子\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }

        Semaphore lock = tenantChatLocks.computeIfAbsent(tenantId, k -> new Semaphore(1));
        if (!lock.tryAcquire()) {
            writeSSEAndClose(response, "data:" + MessageConstant.AI_CHAT_IN_PROGRESS + "\n\n", "data: [DONE]\n\n");
            asyncContext.complete();
            return;
        }

        log.info("AI 找房对话 [tenantId={}]: {}", tenantId,
                message.length() > 50 ? message.substring(0, 50) + "..." : message);

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
                finish(finished, writer, asyncContext, lock, true);
            }

            @Override
            public void onError(AsyncEvent event) {
                finish(finished, writer, asyncContext, lock, true);
            }

            @Override
            public void onComplete(AsyncEvent event) {
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }
        });

        try {
            TokenStream tokenStream = tenantAiAssistant.chat(tenantId, message);
            tokenStream.onPartialResponse(token -> {
                        if (!finished.get()) {
                            writeSSE(writer, token);
                        }
                    })
                    .onCompleteResponse(resp -> finish(finished, writer, asyncContext, lock, false))
                    .onError(error -> {
                        log.error("AI 对话失败 [tenantId={}]", tenantId, error);
                        finish(finished, writer, asyncContext, lock, true);
                    })
                    .start();
        } catch (Exception e) {
            log.error("AI 对话异常 [tenantId={}]", tenantId, e);
            finish(finished, writer, asyncContext, lock, true);
        }
    }

    /** 终态收口：写结束帧、complete、放锁全程只执行一次（容器超时/断开事件与模型回调竞争同一标志位）。 */
    private void finish(AtomicBoolean finished, PrintWriter writer, AsyncContext asyncContext,
                        Semaphore lock, boolean asError) {
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
        lock.release();
    }

    /**
     * 读取对话历史：只回纯文本的「用户 / 助手」消息，工具调用的中间消息直接丢弃
     */
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
            // 与 RedisChatMemoryStore.getMessages 的降级方向保持一致：读失败只当没有历史
            log.warn("读取 AI 对话历史失败（降级为空）[tenantId={}]: {}", tenantId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 清空对话：删除该租客的记忆 key。
     */
    @Override
    public void clearMemory() {
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            throw new NotLoginException(MessageConstant.NOT_LOGIN);
        }
        chatMemoryStore.deleteMessages(tenantId);
        log.info("AI 对话记忆已清空 [tenantId={}]", tenantId);
    }

    /**
     * 把 LangChain4j 的消息映射成前端气泡。
     */
    private AiMessageVO toVO(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.hasSingleText()
                    ? new AiMessageVO(ROLE_USER, userMessage.singleText())
                    : null;
        }
        if (message instanceof AiMessage aiMessage) {
            // AiMessage.text() 只是读字段，无正文时为 null（不会抛异常）
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
