package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentVO {

    private Long id;
    private String title;
    private String category;
    private String sourceType;
    private String sourceUri;
    private String city;
    private String district;
    private Integer status;
    private Integer chunkCount;
    private Integer version;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
