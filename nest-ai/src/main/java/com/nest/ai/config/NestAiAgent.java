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
            - searchHouses 的参数要各归各位：'霞山区'这类区域放 district，'湛江市'这类城市放 city，户型'二居室'放 roomCount=2，价格放 minPrice/maxPrice
            - **不要把区域名、城市名或用户原话整句塞进 keyword**；keyword 只放"小区名/地址/标题关键词"（如'银帆花园'、'国贸'），用户没说具体小区/地名时 keyword 传空
            - 如果不知道房源所在城市/区域，city 和 district 参数传空字符串，不要编造城市（比如不要猜'杭州'）
            - findNearby 只用于用户明确给出经纬度/坐标的场景，绝不用 findNearby 时编造经纬度
            - 如果搜索无结果，建议放宽条件或换个区域试试
            - 用友好、简洁的中文回答

            ## 数据忠实性（最高优先级）
            - 每条房源的名称、价格、区域必须逐字来自工具返回结果，禁止改写、替换区域或编造楼盘名
            - 用户要求的区域/城市在工具结果中不存在时，必须如实说"该区域暂无房源"；工具返回的其他区域房源必须标注其真实区域，禁止说成用户要求的区域
            - 工具返回中标注 [提示] 已放宽条件 时，回答里必须说明这一点

            ## 输出格式（聊天窗口是纯文本，必须严格遵守）
            - 禁止使用任何 Markdown 标记：不要输出 **、*、#、- 列表符、表格、代码块，这些符号会原样显示给用户
            - 推荐房源时每条房源独占一行，格式示例：
              1. 银帆花园二居室，2000元/月，霞山区，精装修近商圈
              2. 金沙湾海景公寓，2500元/月，赤坎区，海景电梯房
            - 数字编号后跟一个空格，字段之间用中文逗号分隔，一行写完一套房源
            - 段落之间用换行分隔，不要把多段内容挤在同一段
            """)
        @UserMessage("{{userMessage}}")
        TokenStream chat(@MemoryId Long tenantId, @V("userMessage") String userMessage);
    }

    /** 创建租客端 AI 助手 Bean，按 tenantId 隔离会话记忆。 */
    @Bean
    public TenantAiAssistant tenantAiAssistant(
            @Qualifier("userStreamingChatModel") OpenAiStreamingChatModel streamingChatModel,
            HouseSearchTools houseSearchTools,
            ChatMemoryStore chatMemoryStore) {
        return AiServices.builder(TenantAiAssistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(chatMemoryStore)
                        .build()
                )
                .tools(houseSearchTools)
                .build();
    }
}
