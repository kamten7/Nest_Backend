package com.nest.AI.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 流式聊天模型配置 —— 房东端 + 租客端独立供应商。
 *
 * <h3>设计</h3>
 * <ul>
 *   <li>房东端（管理端）：temperature=0.1，默认硅基流动 DeepSeek-V3，确保数据查询确定性</li>
 *   <li>租客端（用户端）：temperature=0.7，默认智谱 GLM-4-Flash（免费），支持自然对话</li>
 *   <li>供应商切换只需改 application.yml 配置，代码零改动</li>
 * </ul>
 */
@Configuration
public class AiModelConfig {

    // ==================== 房东端配置 ====================

    @Value("${nest.ai.admin.base-url}")
    private String adminBaseUrl;

    @Value("${nest.ai.admin.api-key}")
    private String adminApiKey;

    @Value("${nest.ai.admin.model-name}")
    private String adminModelName;

    /**
     * 房东端流式聊天模型 —— temperature=0.1，确保数据查询结果确定性。
     */
    @Bean
    public OpenAiStreamingChatModel adminStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(adminBaseUrl)
                .apiKey(adminApiKey)
                .modelName(adminModelName)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    // ==================== 租客端配置 ====================

    @Value("${nest.ai.user.base-url}")
    private String userBaseUrl;

    @Value("${nest.ai.user.api-key}")
    private String userApiKey;

    @Value("${nest.ai.user.model-name}")
    private String userModelName;

    /**
     * 租客端流式聊天模型 —— temperature=0.7，支持自然对话。
     */
    @Bean
    public OpenAiStreamingChatModel userStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(userBaseUrl)
                .apiKey(userApiKey)
                .modelName(userModelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
