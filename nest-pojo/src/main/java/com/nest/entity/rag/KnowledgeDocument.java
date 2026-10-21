package com.nest.entity.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private Long id;
    private String title;
    private String category;
    private String sourceType;
    private String sourceUri;
    private String city;
    private String district;

    private Integer status;
    private Integer chunkCount;
    private String contentHash;
    private Integer version;
    private String errorMsg;

    private String content;

    private Long createdBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
