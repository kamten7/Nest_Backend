package com.nest.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunkVO {

    private Long docId;
    private String title;
    private String category;
    private String city;
    private String district;
    private Integer chunkIndex;
    private String content;
    private String source;
    private Double score;
}
