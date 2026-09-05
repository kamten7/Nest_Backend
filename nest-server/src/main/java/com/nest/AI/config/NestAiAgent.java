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
 * 基于 LangChain4j AiServices 构建，租客端模型（GLM-4-Flash）：
 * 
 * 租客发消息 → TenantAiAssistant.chat()
 *   → AI 自动决策 → 调用 @Tool（HouseSearchTools）
 *   → 拿到真实房源数据 → LLM 润色 → TokenStream 流式返回
 * 
 *
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
            - 禁止凭空编造城市/区域/预算/经纬度等参数

            ## 参数传递（重要）
            - 用户未提及预算/价格时，minPrice、maxPrice 一律传 null，禁止传 0
            - 用户未提及房型（几居）时，roomCount 传 null
            - 只有用户明确给出预算、房型、区域时，才传对应参数

            ## 行为规范
            - 拿到工具返回的房源数据后，用自然语言呈现，不要输出 JSON 或原始格式
            - 用户只问'推荐/有什么好房/随便看看'这类泛化需求时，用 recommendHouses 按评论推荐 2-3 套（含评分），不要给 searchHouses 乱塞价格或房型
            - 用户给出具体条件（城市/预算/房型）时，用 searchHouses 精准搜索
            - 用户说'XX小区/XX地名附近有什么房子'时，用 searchHouses 把地名作为 keyword 搜索（如'银帆花园附近'→keyword='银帆花园'），且不要传价格/房型参数
            - 如果不知道房源所在城市/区域，city 和 district 参数传空字符串，不要编造城市（比如不要猜'杭州'）
            - findNearby 只用于用户明确给出经纬度/坐标的场景，绝不用 findNearby 时编造经纬度
            - 如果搜索无结果，建议放宽条件或换个区域试试，不要直接说"没有房源"
            - 用友好、简洁的中文回答

            ## 输出格式（重要）
            - 房源结果必须逐条列出，每条**单独占一行、用换行分隔**，严禁挤成一行
            - 每条统一格式：'1. 标题，价格X元/月，位置XX，房源ID N'
            - 禁止用连字符或顿号把多条拼成一行（如'-价格-位置-房源ID'）
            - 房源ID 单独写明（如'房源ID:3'），不要与后面的句子粘连
            - 结尾的补充语（如'需要看房/预约请告诉我'）单独起一行
            """)
        @UserMessage("{{userMessage}}")
        TokenStream chat(@V("userMessage") String userMessage);
        // 处理用户消息，返回流式 TokenStream（SSE 逐字输出）
    }

    /**
     * 创建租客端 AI 助手 Bean（单例，整个应用共享一个）。
     *
     * 用 LangChain4j 的 AiServices 构建"真 Function Calling"代理：
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
