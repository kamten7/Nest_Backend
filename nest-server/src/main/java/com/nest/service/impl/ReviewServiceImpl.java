package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.entity.House;
import com.nest.entity.Landlord;
import com.nest.entity.Review;
import com.nest.entity.ReviewComment;
import com.nest.entity.ReviewCommentLike;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.ReviewCommentLikeMapper;
import com.nest.mapper.ReviewCommentMapper;
import com.nest.mapper.ReviewMapper;
import com.nest.mapper.TenantMapper;
import com.nest.service.ReviewService;
import com.nest.vo.ReviewCommentVO;
import com.nest.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final ReviewCommentLikeMapper reviewCommentLikeMapper;
    private final HouseMapper houseMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;

    /**
     * 发表评论（租客端，一房一评）。
     *
     * 流程：校验房源存在且上架 → 校验评分范围（1-5）→ 防重复（应用层先查 + 数据库唯一约束兜底）→ 落库。
     *
     * @param houseId 房源 ID
     * @param rating  评分（1-5）
     * @param content 评论内容
     * @return 新建评论的主键 ID
     */
    @Override
    @Transactional
    public Long createReview(Long houseId, Integer rating, String content) {
        Long tenantId = BaseContext.getCurrentId();   // 取当前登录租客 ID

        // 校验房源存在且上架（已下架的房源不能评论）
        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() == 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        // 校验评分（1-5 分，防止非法值）
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分必须是 1-5 分");
        }
        // 防重复（uk_tenant_house 唯一约束兜底）——先应用层查询避免多次插库
        Review existing = reviewMapper.selectByTenantAndHouse(tenantId, houseId);
        if (existing != null) {
            throw new BusinessException(MessageConstant.REVIEW_DUPLICATE);
        }

        Review review = Review.builder()
                .tenantId(tenantId)
                .houseId(houseId)
                .rating(rating)
                .content(content)
                .build();
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // 数据库唯一约束兜底：并发下两条同时插入，后到的触发约束
            throw new BusinessException(MessageConstant.REVIEW_DUPLICATE);
        }
        log.info("发表评论: id={}, tenantId={}, houseId={}, rating={}", review.getId(), tenantId, houseId, rating);
        return review.getId();
    }

    @Override
    public PageResult<ReviewVO> listByHouse(Long houseId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Review> reviews = reviewMapper.selectByHouse(houseId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);

        // 房源平均分 + 总数
        BigDecimal avgRating = reviewMapper.selectAvgRatingByHouse(houseId);
        long totalCount = reviewMapper.selectCountByHouse(houseId);

        // 当前登录用户（可能未登录，评论列表是公开接口）
        Long currentUserId = BaseContext.getCurrentId();
        String currentType = BaseContext.getCurrentType();

        List<ReviewVO> vos = new ArrayList<>();
        for (Review r : reviews) {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setHouseId(r.getHouseId());
            vo.setTenantId(r.getTenantId());
            vo.setRating(r.getRating());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            vo.setAvgRating(avgRating);
            vo.setTotalCount(totalCount);

            // 租客信息
            Tenant tenant = tenantMapper.selectById(r.getTenantId());
            if (tenant != null) {
                vo.setTenantName(tenant.getNickname() != null ? tenant.getNickname() : "租客" + tenant.getId());
                vo.setTenantAvatar(tenant.getAvatar());
            }

            // 该评论的回复
            List<ReviewComment> comments = reviewCommentMapper.selectByReviewId(r.getId());
            vo.setComments(buildCommentVOs(comments, currentUserId, currentType));
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 回复评论（租客或房东）。
     *
     * 评论者身份（userType/userId）从 {@link BaseContext} 取，不信任前端。
     *
     * @param reviewId 被回复的评论 ID
     * @param content  回复内容
     * @param parentId 父回复 ID（NULL=一级回复，用于嵌套）
     * @return 新建回复的主键 ID
     */
    @Override
    @Transactional
    public Long addComment(Long reviewId, String content, Long parentId) {
        // 校验评论存在
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }

        // 评论者身份：从 BaseContext 取（租客或房东，表里用 user_type 区分）
        Long userId = BaseContext.getCurrentId();
        String userType = BaseContext.getCurrentType();

        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId)
                .userType(userType)   // tenant / landlord
                .userId(userId)
                .content(content)
                .parentId(parentId)   // 嵌套回复：指向父回复 ID
                .build();
        reviewCommentMapper.insert(comment);
        log.info("回复评论: id={}, reviewId={}, userType={}, userId={}", comment.getId(), reviewId, userType, userId);
        return comment.getId();
    }

    /**
     * 点赞/取消点赞（可切换）。
     *
     * liked=true：若未赞过，插入点赞记录 + 评论 like_count+1（幂等：已赞则忽略）；
     * liked=false：若已赞过，删点赞记录 + like_count-1。
     *
     * @param commentId 被点赞的回复 ID
     * @param liked     目标状态：true=点赞，false=取消
     */
    @Override
    @Transactional
    public void likeComment(Long commentId, boolean liked) {
        // 点赞人用租客身份（review_comment_like 表字段是 tenant_id）
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }

        ReviewComment comment = reviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(MessageConstant.REVIEW_COMMENT_NOT_FOUND);
        }

        ReviewCommentLike existing = reviewCommentLikeMapper.selectByCommentAndTenant(commentId, tenantId);

        if (liked) {
            // 点赞：已赞则忽略（幂等），未赞则插入 + 计数
            if (existing == null) {
                reviewCommentLikeMapper.insert(ReviewCommentLike.builder()
                        .commentId(commentId)
                        .tenantId(tenantId)
                        .build());
                reviewCommentMapper.incrementLikeCount(commentId);
            }
        } else {
            // 取消赞：已赞则删除 + 减计数
            if (existing != null) {
                reviewCommentLikeMapper.deleteByCommentAndTenant(commentId, tenantId);
                reviewCommentMapper.decrementLikeCount(commentId);
            }
        }
        log.info("评论点赞切换: commentId={}, tenantId={}, liked={}", commentId, tenantId, liked);
    }

    // ==================== 内部方法 ====================

    /** 组装回复 VO（含评论者名 + 点赞状态） */
    private List<ReviewCommentVO> buildCommentVOs(List<ReviewComment> comments, Long currentUserId, String currentType) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }
        List<ReviewCommentVO> vos = new ArrayList<>();
        for (ReviewComment c : comments) {
            ReviewCommentVO vo = new ReviewCommentVO();
            vo.setId(c.getId());
            vo.setReviewId(c.getReviewId());
            vo.setUserType(c.getUserType());
            vo.setUserId(c.getUserId());
            vo.setContent(c.getContent());
            vo.setParentId(c.getParentId());
            vo.setLikeCount(c.getLikeCount());
            vo.setCreateTime(c.getCreateTime());

            // 评论者昵称（租客或房东）
            if ("landlord".equals(c.getUserType())) {
                Landlord landlord = landlordMapper.selectById(c.getUserId());
                vo.setUserName(landlord != null && landlord.getName() != null ? landlord.getName() : "房东");
            } else {
                Tenant tenant = tenantMapper.selectById(c.getUserId());
                vo.setUserName(tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客");
            }

            // 当前用户是否已赞（当前登录的租客）
            vo.setLiked(currentUserId != null
                    && reviewCommentLikeMapper.selectByCommentAndTenant(c.getId(), currentUserId) != null);
            vos.add(vo);
        }
        return vos;
    }
}
