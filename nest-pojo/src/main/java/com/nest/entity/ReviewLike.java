package com.nest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 顶楼评价点赞实体（防止重复点赞）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewLike {

    private Long id;
    private Long reviewId;
    /** 点赞者类型 tenant/landlord */
    private String userType;
    private Long userId;
    private LocalDateTime createTime;
}
