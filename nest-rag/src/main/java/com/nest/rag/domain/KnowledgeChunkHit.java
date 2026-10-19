package com.nest.rag.domain;

import lombok.Data;

@Data
public class KnowledgeChunkHit {

    private Long chunkId;
    private Long docId;
    private Integer chunkIndex;
    private String content;
    private String title;
    private String category;
    private String city;
    private String district;
    private String source;
    private Double score;
}
