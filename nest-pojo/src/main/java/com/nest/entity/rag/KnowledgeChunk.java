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
public class KnowledgeChunk {

    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private String embeddingId;
    private LocalDateTime createTime;
}
