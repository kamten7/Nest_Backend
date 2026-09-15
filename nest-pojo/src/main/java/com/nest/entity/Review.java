package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 房源评论实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    private Long id;
    private Long tenantId;
    private Long houseId;
    private Integer rating;
    private String content;
    private LocalDateTime createTime;
}
