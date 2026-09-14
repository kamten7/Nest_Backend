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

/**
 * AI 对话记忆的 Redis 存储实现
 * LangChain4j 把
 * "记忆存哪里"抽象成 { ChatMemoryStore }，
 * "留多少条"由MessageWindowChatMemory 负责
 * 本类只做存储：把每个 memoryId（= tenantId）的记忆
 * 序列化成 JSON 存进 Redis，并带 TTL 自动过期
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    /** Redis key 前缀，便于排查时用 keys 一次性找出来。 */
    private static final String KEY_PREFIX = "nest:ai:memory:";

    /** 记忆保留时长：7 天没说话的会话自动清掉，避免 Redis 无限堆积。 */
    private static final Duration TTL = Duration.ofDays(7);

    /** Redis 模板 */
    private final StringRedisTemplate redisTemplate;

    /** memoryId 这里就是租客 ID（接口上标了 @MemoryId Long tenantId）。 */
    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }

    /** 取记忆：没有就返回空列表。★ 绝不能返回 null。 */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String json = redisTemplate.opsForValue().get(key(memoryId));
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            // Redis 故障 → 当作"失忆"，不让对话直接 500
            log.warn("读取 AI 记忆失败（降级为空）memoryId={}: {}", memoryId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 存记忆：整体覆盖写入，并刷新 TTL。 */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            redisTemplate.opsForValue().set(
                    key(memoryId),
                    ChatMessageSerializer.messagesToJson(messages),
                    TTL);
        } catch (Exception e) {
            // 写失败只记日志，不影响已返回给用户的回答
            log.warn("写入 AI 记忆失败 memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    /** 删记忆：会话结束或用户点"清空对话"时调用。 */
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(key(memoryId));
    }
}