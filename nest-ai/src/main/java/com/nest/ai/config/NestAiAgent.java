package com.nest.ai.config;

import com.nest.ai.tools.HouseSearchTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/** Nest AI 找房助手 —— 基于 LangChain4j AiServices 的 Function Calling Agent。 */
@Configuration
public class NestAiAgent {

    /** 租客端 AI 找房助手接口。 */
    public interface TenantAiAssistant {

        @SystemMessage("""
            你是 Nest 安居的 AI 找房助手'小巢'，帮助用户找房子。

            ## 绝对禁止
            - 禁止捏造不存在的房源信息，只能使用工具返回的真实数据
            - 禁止泄露房东的个人信息（电话等）给非预约用户
            - 涉及预约看房、收藏等操作时，必须先向用户确认后再执行

            ## 行为规范
            - 拿到工具返回的房源数据后，用自然语言呈现，不要输出 JSON 或原始格式
            - 推荐时列出 3-5 个最匹配的房源，含价格、位置、亮点标签
            - 用户说'XX小区/XX地名附近有什么房子'时，用 searchHouses 把地名作为 keyword 搜索（如'银帆花园附近'→keyword='银帆花园'）
            - 如果不知道房源所在城市/区域，city 和 district 参数传空字符串，不要编造城市（比如不要猜'杭州'）
            - findNearby 只用于用户明确给出经纬度/坐标的场景，绝不用 findNearby 时编造经纬度
            - 如果搜索无结果，建议放宽条件或换个区域试试
            - 用友好、简洁的中文回答
            """)
        @UserMessage("{{userMessage}}")
        TokenStream chat(@MemoryId Long tenantId, @V("userMessage") String userMessage);
    }

    /** 创建租客端 AI 助手 Bean。 */
    @Bean
    public TenantAiAssistant tenantAiAssistant(
            @Qualifier("userStreamingChatModel") OpenAiStreamingChatModel streamingChatModel,
            HouseSearchTools houseSearchTools,
            ChatMemoryStore chatMemoryStore) {
        return AiServices.builder(TenantAiAssistant.class)
                .streamingChatModel(streamingChatModel)
                // 按 @MemoryId（tenantId）隔离对话记忆：LangChain4j 内部用
                // ConcurrentHashMap.computeIfAbsent 缓存，同一个 memoryId 只会调用一次本 provider。
                // 用单例 chatMemory() 会让所有租客共用同一段上下文，造成跨用户串号/隐私泄露。
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)//id绑定memoryId，确保隔离
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build()
                )
                .tools(houseSearchTools)//添加工具
                .build();
    }
}
