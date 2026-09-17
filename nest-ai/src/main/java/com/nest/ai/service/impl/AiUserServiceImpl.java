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
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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

    public AiUserServiceImpl(TenantAiAssistant tenantAiAssistant, ChatMemoryStore chatMemoryStore) {
        this.tenantAiAssistant = tenantAiAssistant;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
    /** 流式 AI 找房对话（SSE）；catch 用 Exception 而非 IOException 以兜住所有异常。 */
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
            TokenStream tokenStream = tenantAiAssistant.chat(tenantId, message);
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
        } catch (Exception e) {
            log.error("AI 对话异常 [tenantId={}]", tenantId, e);
            try {
                writeSSE(response.getWriter(), "[ERROR] " + MessageConstant.AI_SERVICE_ERROR);
                writeSSE(response.getWriter(), "[DONE]");
            } catch (Exception ignored) { }
            asyncContext.complete();
        }
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
     *
     * <p>MessageWindowChatMemory 无本地状态（每次现从 store 读），所以删掉 key 就生效，
     * 下一条消息会自然重建一份空白记忆，不需要「新建会话」这一步。</p>
     *
     * <p>⚠️ 已知边界：若清空时服务端仍有流式请求在跑，该请求结束时会整份写回旧记忆，
     * 表现为「清空失效」。前端已在 streaming 期间禁用清空按钮来规避。</p>
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
