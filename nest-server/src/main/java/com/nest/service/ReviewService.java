package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.ReviewVO;

/** 评论服务接口 */
public interface ReviewService {

    /** 发表评论（租客；rating 可为空 = 纯评论/提问） */
    Long createReview(Long houseId, Integer rating, String content);

    /** 房源评论列表（含回复、点赞状态，公开） */
    PageResult<ReviewVO> listByHouse(Long houseId, Integer page, Integer pageSize);

    /** 回复评论（租客或房东，支持嵌套追问） */
    Long addComment(Long reviewId, String content, Long parentId);

    /** 顶楼评价点赞/取消点赞（可切换，幂等） */
    void likeReview(Long reviewId, boolean liked);

    /** 评论回复点赞/取消点赞（可切换，幂等） */
    void likeComment(Long commentId, boolean liked);

    /** 当前租客是否已对该房源发表过评论（退租后弹评价用） */
    boolean hasReviewed(Long houseId);

    /** 删除自己发的评论/评价（级联清理其下回复与点赞） */
    void deleteReview(Long reviewId);

    /** 删除自己发的回复（级联清理其子回复与点赞） */
    void deleteComment(Long commentId);
}
