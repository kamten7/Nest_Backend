package com.nest.service.impl;

import com.nest.entity.AiTrace;
import com.nest.mapper.AiTraceMapper;
import com.nest.service.AiTraceStore;
import com.nest.vo.AiTraceRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTraceStoreImpl implements AiTraceStore {

    private final AiTraceMapper aiTraceMapper;

    @Async("aiTraceExecutor")
    @Override
    public void save(AiTraceRecord record) {
        if (record == null || record.getTraceId() == null) {
            return;
        }
        try {
            aiTraceMapper.insert(AiTrace.builder()
                    .traceId(record.getTraceId())
                    .tenantId(record.getTenantId())
                    .channel(record.getChannel())
                    .questionPreview(record.getQuestionPreview())
                    .toolCalls(record.getToolCallsJson())
                    .retrievedDocs(record.getRetrievedDocsJson())
                    .latencyMs(record.getLatencyMs())
                    .promptTokens(record.getPromptTokens())
                    .completionTokens(record.getCompletionTokens())
                    .model(record.getModel())
                    .success(Boolean.FALSE.equals(record.getSuccess()) ? 0 : 1)
                    .errorCode(record.getErrorCode())
                    .build());
        } catch (Exception e) {
            log.warn("AI 轨迹落库失败（不影响对话）: traceId={}, {}", record.getTraceId(), e.getMessage());
        }
    }
}
