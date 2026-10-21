package com.nest.entity.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTrace {

    private Long id;
    private String traceId;
    private Long tenantId;
    private String channel;
    private String questionPreview;
    private String toolCalls;
    private String retrievedDocs;
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String model;
    private Integer success;
    private String errorCode;
    private LocalDateTime createTime;
}
