package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.ReviewVO;

/** 评论服务接口 */
public interface ReviewService {

    /** 发表评论（租客，一房一评） */
    Long createReview(Long houseId, Integer rating, String content);

    /** 房源评论列表（含回复，公开） */
    PageResult<ReviewVO> listByHouse(Long houseId, Integer page, Integer pageSize);

    /** 回复评论（租客或房东） */
    Long addComment(Long reviewId, String content, Long parentId);

    /** 点赞/取消点赞（可切换） */
    void likeComment(Long commentId, boolean liked);
}
