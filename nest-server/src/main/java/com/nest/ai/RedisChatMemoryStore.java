package com.nest.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** AI 对话记忆 Redis 存储（按 memoryId 隔离，JSON+TTL） */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "nest:ai:memory:";

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /** memoryId 即租客 ID（@MemoryId Long tenantId） */
    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    /** 取记忆；无则返回空列表，禁止返回 null */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = redisTemplate.opsForValue().get(key(memoryId));
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.warn("读取 AI 记忆失败（降级为空）memoryId={}: {}", memoryId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 存记忆：整体覆盖写入并刷新 TTL */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            redisTemplate.opsForValue().set(
                    key(memoryId),
                    ChatMessageSerializer.messagesToJson(messages),
                    TTL);
        } catch (Exception e) {
            log.warn("写入 AI 记忆失败 memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    /** 删记忆（会话结束/清空对话时调用） */
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(key(memoryId));
    }
}