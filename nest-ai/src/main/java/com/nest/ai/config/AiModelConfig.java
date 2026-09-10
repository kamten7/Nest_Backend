package com.nest.ai.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** LangChain4j 流式聊天模型配置 —— 房东端 + 租客端独立供应商。 */
@Configuration
public class AiModelConfig {

    @Value("${nest.ai.admin.base-url}")
    private String adminBaseUrl;

    @Value("${nest.ai.admin.api-key}")
    private String adminApiKey;

    @Value("${nest.ai.admin.model-name}")
    private String adminModelName;

    /** 房东端流式聊天模型 —— temperature=0.1，确保数据查询结果确定性。 */
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

    @Value("${nest.ai.user.base-url}")
    private String userBaseUrl;

    @Value("${nest.ai.user.api-key}")
    private String userApiKey;

    @Value("${nest.ai.user.model-name}")
    private String userModelName;

    /** 租客端流式聊天模型 —— temperature=0.7，支持自然对话。 */
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
