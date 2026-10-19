package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTraceRecord {

    private String traceId;
    private Long tenantId;
    private String channel;
    private String questionPreview;
    private String toolCallsJson;
    private String retrievedDocsJson;
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String model;
    private Boolean success;
    private String errorCode;
}
