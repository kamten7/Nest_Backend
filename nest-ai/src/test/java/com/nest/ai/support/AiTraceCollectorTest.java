package com.nest.ai.support;

import com.nest.vo.AiTraceRecord;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTraceCollectorTest {

    @Test
    void capturesTokensAndTruncatesQuestionPreview() {
        ChatResponse response = mock(ChatResponse.class);
        when(response.modelName()).thenReturn("deepseek-chat");
        when(response.tokenUsage()).thenReturn(new TokenUsage(120, 30, 150));

        AiTraceCollector collector = new AiTraceCollector("trace-1", 7L, "user", "房".repeat(300));
        collector.onResponse(response);
        AiTraceRecord record = collector.toRecord(true, 1234);

        assertThat(record.getTraceId()).isEqualTo("trace-1");
        assertThat(record.getTenantId()).isEqualTo(7L);
        assertThat(record.getQuestionPreview()).hasSize(200);
        assertThat(record.getPromptTokens()).isEqualTo(120);
        assertThat(record.getCompletionTokens()).isEqualTo(30);
        assertThat(record.getModel()).isEqualTo("deepseek-chat");
        assertThat(record.getLatencyMs()).isEqualTo(1234);
        assertThat(record.getSuccess()).isTrue();
    }

    @Test
    void recordsError() {
        AiTraceCollector collector = new AiTraceCollector("trace-2", 7L, "user", "押金能退吗");
        collector.onError(new IllegalStateException("model timeout"));

        AiTraceRecord record = collector.toRecord(false, 500);

        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getErrorCode()).contains("model timeout");
    }
}
