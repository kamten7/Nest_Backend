package com.nest.ai.support;

import com.alibaba.fastjson2.JSON;
import com.nest.vo.ai.AiTraceRecord;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiTraceCollector {

    private final String traceId;
    private final Long tenantId;
    private final String channel;
    private final String questionPreview;

    private final List<Map<String, Object>> toolCalls = new ArrayList<>();
    private volatile Integer promptTokens;
    private volatile Integer completionTokens;
    private volatile String model;
    private volatile String error;

    public AiTraceCollector(String traceId, Long tenantId, String channel, String question) {
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.channel = channel;
        this.questionPreview = question == null ? null
                : (question.length() <= 200 ? question : question.substring(0, 200));
    }

    public synchronized void onToolExecuted(ToolExecution execution) {
        if (execution == null || execution.request() == null) {
            return;
        }
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("name", execution.request().name());
        call.put("args", abbreviate(execution.request().arguments(), 300));
        call.put("durationMs", execution.duration() == null ? null : execution.duration().toMillis());
        call.put("success", !execution.hasFailed());
        toolCalls.add(call);
    }

    public void onResponse(ChatResponse response) {
        if (response == null) {
            return;
        }
        model = response.modelName();
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            promptTokens = usage.inputTokenCount();
            completionTokens = usage.outputTokenCount();
        }
    }

    public void onError(Throwable throwable) {
        error = throwable == null ? "unknown" : abbreviate(throwable.getMessage(), 100);
    }

    public AiTraceRecord toRecord(boolean success, long latencyMs) {
        return AiTraceRecord.builder()
                .traceId(traceId)
                .tenantId(tenantId)
                .channel(channel)
                .questionPreview(questionPreview)
                .toolCallsJson(toolCalls.isEmpty() ? null : JSON.toJSONString(toolCalls))
                .retrievedDocsJson(null)
                .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .model(model)
                .success(success)
                .errorCode(error)
                .build();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
