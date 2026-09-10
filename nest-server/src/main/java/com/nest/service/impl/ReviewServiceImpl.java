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

/** 评论服务实现。 */
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

    /** 发表评论（租客端，一房一评）。 */
    @Override
    @Transactional
    public Long createReview(Long houseId, Integer rating, String content) {
        Long tenantId = BaseContext.getCurrentId();

        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() == 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分必须是 1-5 分");
        }
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
            throw new BusinessException(MessageConstant.REVIEW_DUPLICATE);
        }
        log.info("发表评论: id={}, tenantId={}, houseId={}, rating={}", review.getId(), tenantId, houseId, rating);
        return review.getId();
    }

    /** 查询房源评论列表（分页），含平均分、回复、点赞状态。 */
    @Override
    public PageResult<ReviewVO> listByHouse(Long houseId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Review> reviews = reviewMapper.selectByHouse(houseId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);

        BigDecimal avgRating = reviewMapper.selectAvgRatingByHouse(houseId);
        long totalCount = reviewMapper.selectCountByHouse(houseId);

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

            Tenant tenant = tenantMapper.selectById(r.getTenantId());
            if (tenant != null) {
                vo.setTenantName(tenant.getNickname() != null ? tenant.getNickname() : "租客" + tenant.getId());
                vo.setTenantAvatar(tenant.getAvatar());
            }

            List<ReviewComment> comments = reviewCommentMapper.selectByReviewId(r.getId());
            vo.setComments(buildCommentVOs(comments, currentUserId, currentType));
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 回复评论（租客或房东）。 */
    @Override
    @Transactional
    public Long addComment(Long reviewId, String content, Long parentId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }

        Long userId = BaseContext.getCurrentId();
        String userType = BaseContext.getCurrentType();

        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId)
                .userType(userType)
                .userId(userId)
                .content(content)
                .parentId(parentId)
                .build();
        reviewCommentMapper.insert(comment);
        log.info("回复评论: id={}, reviewId={}, userType={}, userId={}", comment.getId(), reviewId, userType, userId);
        return comment.getId();
    }

    /** 点赞/取消点赞（可切换，幂等）。 */
    @Override
    @Transactional
    public void likeComment(Long commentId, boolean liked) {
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
            if (existing == null) {
                reviewCommentLikeMapper.insert(ReviewCommentLike.builder()
                        .commentId(commentId)
                        .tenantId(tenantId)
                        .build());
                reviewCommentMapper.incrementLikeCount(commentId);
            }
        } else {
            if (existing != null) {
                reviewCommentLikeMapper.deleteByCommentAndTenant(commentId, tenantId);
                reviewCommentMapper.decrementLikeCount(commentId);
            }
        }
        log.info("评论点赞切换: commentId={}, tenantId={}, liked={}", commentId, tenantId, liked);
    }

    /** 组装回复 VO（含评论者名 + 点赞状态）。 */
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

            if ("landlord".equals(c.getUserType())) {
                Landlord landlord = landlordMapper.selectById(c.getUserId());
                vo.setUserName(landlord != null && landlord.getName() != null ? landlord.getName() : "房东");
            } else {
                Tenant tenant = tenantMapper.selectById(c.getUserId());
                vo.setUserName(tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客");
            }

            vo.setLiked(currentUserId != null
                    && reviewCommentLikeMapper.selectByCommentAndTenant(c.getId(), currentUserId) != null);
            vos.add(vo);
        }
        return vos;
    }
}
