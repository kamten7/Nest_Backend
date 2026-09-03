package com.nest.AI.config;

import com.nest.AI.tools.HouseSearchTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nest AI 找房助手 —— 真 Function Calling Agent。
 *
 * <p>基于 LangChain4j {@code AiServices} 构建，租客端模型（GLM-4-Flash）：
 * <pre>
 * 租客发消息 → TenantAiAssistant.chat()
 *   → AI 自动决策 → 调用 @Tool（HouseSearchTools）
 *   → 拿到真实房源数据 → LLM 润色 → TokenStream 流式返回
 * </pre>
 *
 * <p>关键设计（吸取外卖教训）：</p>
 * <ul>
 *   <li>写操作（预约/收藏）System Prompt 强制「先确认再执行」</li>
 *   <li>工具只读，无共享实例字段，天然无并发串号风险</li>
 *   <li>进程内 {@link MessageWindowChatMemory}，最近 20 条对话上下文</li>
 * </ul>
 */
@Configuration
public class NestAiAgent {

    /**
     * 租客端 AI 找房助手接口。
     * SystemMessage 定义角色和安全约束，UserMessage 注入用户问题。
     */
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
        TokenStream chat(@V("userMessage") String userMessage);
        // 处理用户消息，返回流式 TokenStream（SSE 逐字输出）
    }

    /**
     * 创建租客端 AI 助手 Bean（单例，整个应用共享一个）。
     *
     * 用 LangChain4j 的 {@code AiServices} 构建"真 Function Calling"代理：
     * AI 自动决策要调哪个工具、传什么参数，工具结果喂回 LLM 后组织成自然语言回答。
     *
     * @param streamingChatModel 租客端流式模型（GLM-4-Flash，支持 TokenStream 流式输出）
     * @param houseSearchTools   房源查询工具集（@Tool 注解，AI 可调用的真实数据源）
     * @return 构建好的 AI 助手单例
     */
    @Bean
    public TenantAiAssistant tenantAiAssistant(
            @Qualifier("userStreamingChatModel") OpenAiStreamingChatModel streamingChatModel,
            HouseSearchTools houseSearchTools) {
        return AiServices.builder(TenantAiAssistant.class)   // 指定代理接口
                .streamingChatModel(streamingChatModel)      // 挂流式模型（SSE 逐字输出）
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))   // 进程内记忆：保留最近 20 条对话，支持多轮上下文
                .tools(houseSearchTools)                     // 注册工具集：AI 可调这些只读工具拿真实房源数据
                .build();                                    // 构建代理实例
    }
}
