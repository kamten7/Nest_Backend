package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageParam;
import com.nest.common.PageResult;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.entity.House;
import com.nest.entity.Landlord;
import com.nest.entity.Review;
import com.nest.entity.ReviewComment;
import com.nest.entity.ReviewCommentLike;
import com.nest.entity.ReviewLike;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.ReviewCommentLikeMapper;
import com.nest.mapper.ReviewCommentMapper;
import com.nest.mapper.ReviewLikeMapper;
import com.nest.mapper.ReviewMapper;
import com.nest.mapper.TenantMapper;
import com.nest.order.mapper.RentOrderMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    /** 单页最大条数，防止一次拉爆整张表 */
    private static final int MAX_PAGE_SIZE = 50;
    /** 评论内容长度上限 */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final ReviewMapper reviewMapper;
    private final ReviewCommentMapper reviewCommentMapper;
    private final ReviewCommentLikeMapper reviewCommentLikeMapper;
    private final ReviewLikeMapper reviewLikeMapper;
    private final HouseMapper houseMapper;
    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;
    private final RentOrderMapper rentOrderMapper;

    /** 发表评论（租客端；rating 为 null 表示纯评论/提问，不参与平均分）。 */
    @Override
    @Transactional
    public Long createReview(Long houseId, Integer rating, String content) {
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }

        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        // 房源已下架时，只有租过这套房的人（典型场景：退租后回来评价）才允许发评论
        if (house.getStatus() != null && house.getStatus() == 0
                && rentOrderMapper.countByTenantAndHouse(tenantId, houseId) == 0) {
            throw new BusinessException(MessageConstant.REVIEW_HOUSE_FORBIDDEN);
        }

        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BusinessException(MessageConstant.REVIEW_RATING_INVALID);
        }
        String text = validateContent(content);

        Review review = Review.builder()
                .tenantId(tenantId)
                .houseId(houseId)
                .rating(rating)
                .content(text)
                .build();
        reviewMapper.insert(review);
        log.info("发表评论: id={}, tenantId={}, houseId={}, rating={}",
                review.getId(), tenantId, houseId, rating);
        return review.getId();
    }

    /** 查询房源评论列表（分页），含平均分、楼中回复、点赞状态。 */
    @Override
    public PageResult<ReviewVO> listByHouse(Long houseId, Integer page, Integer pageSize) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);

        PageHelper.startPage(PageParam.pageOf(pageNum), PageParam.pageSizeOf(size));
        List<Review> reviews = reviewMapper.selectByHouse(houseId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);

        // AVG/COUNT(rating) 忽略 NULL ⇒ 未打分的纯评论不拉低均分、也不计入评分人数
        BigDecimal avgRating = reviewMapper.selectAvgRatingByHouse(houseId);
        long totalCount = reviewMapper.selectCountByHouse(houseId);
        long ratedCount = reviewMapper.selectRatedCountByHouse(houseId);

        Long currentId = BaseContext.getCurrentId();
        String currentType = BaseContext.getCurrentType();
        boolean loggedIn = currentId != null && currentType != null;

        List<Long> reviewIds = reviews.stream().map(Review::getId).collect(Collectors.toList());

        // 一次性取出本页所有回复，避免「每条评价查一次回复」（N+1）
        List<ReviewComment> allComments = reviewIds.isEmpty()
                ? Collections.emptyList()
                : reviewCommentMapper.selectByReviewIds(reviewIds);
        Map<Long, List<ReviewComment>> commentsByReview = allComments.stream()
                .collect(Collectors.groupingBy(ReviewComment::getReviewId));
        Map<Long, ReviewComment> commentById = allComments.stream()
                .collect(Collectors.toMap(ReviewComment::getId, c -> c, (a, b) -> a));

        // 一次性取出当前用户在本页评价/回复上的点赞记录
        Set<Long> likedReviewIds = new HashSet<>();
        if (loggedIn && !reviewIds.isEmpty()) {
            reviewLikeMapper.selectByUserAndReviewIds(reviewIds, currentType, currentId)
                    .forEach(l -> likedReviewIds.add(l.getReviewId()));
        }
        Set<Long> likedCommentIds = new HashSet<>();
        if (loggedIn && !allComments.isEmpty()) {
            List<Long> commentIds = allComments.stream().map(ReviewComment::getId).collect(Collectors.toList());
            reviewCommentLikeMapper.selectByUserAndCommentIds(commentIds, currentType, currentId)
                    .forEach(l -> likedCommentIds.add(l.getCommentId()));
        }

        Map<String, String> userNameCache = new HashMap<>();

        List<ReviewVO> vos = new ArrayList<>();
        for (Review r : reviews) {
            ReviewVO vo = new ReviewVO();
            vo.setId(r.getId());
            vo.setHouseId(r.getHouseId());
            vo.setTenantId(r.getTenantId());
            vo.setRating(r.getRating());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            vo.setLikeCount(r.getLikeCount() == null ? 0 : r.getLikeCount());
            vo.setLiked(likedReviewIds.contains(r.getId()));
            vo.setMine(isMine(JwtConstant.TYPE_TENANT, r.getTenantId(), currentId, currentType));
            vo.setAvgRating(avgRating);
            vo.setTotalCount(totalCount);
            vo.setRatedCount(ratedCount);
            vo.setTenantName(resolveUserName(JwtConstant.TYPE_TENANT, r.getTenantId(), userNameCache));
            vo.setTenantAvatar(resolveTenantAvatar(r.getTenantId()));

            List<ReviewComment> comments = commentsByReview.getOrDefault(r.getId(), Collections.emptyList());
            vo.setComments(buildCommentVOs(comments, commentById, likedCommentIds, userNameCache,
                    currentId, currentType));
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 回复评论（租客或房东，支持嵌套追问）。 */
    @Override
    @Transactional
    public Long addComment(Long reviewId, String content, Long parentId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }
        String text = validateContent(content);

        // parentId 必须属于同一条评价，否则会串楼（把回复挂到别的评价下面）
        if (parentId != null) {
            ReviewComment parent = reviewCommentMapper.selectById(parentId);
            if (parent == null || !reviewId.equals(parent.getReviewId())) {
                throw new BusinessException(MessageConstant.REVIEW_PARENT_NOT_FOUND);
            }
        }

        Long userId = BaseContext.getCurrentId();
        String userType = BaseContext.getCurrentType();
        if (userId == null || userType == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }

        ReviewComment comment = ReviewComment.builder()
                .reviewId(reviewId)
                .userType(userType)
                .userId(userId)
                .content(text)
                .parentId(parentId)
                .build();
        reviewCommentMapper.insert(comment);
        log.info("回复评论: id={}, reviewId={}, userType={}, userId={}, parentId={}",
                comment.getId(), reviewId, userType, userId, parentId);
        return comment.getId();
    }

    /** 顶楼评价点赞/取消点赞（可切换，幂等）。 */
    @Override
    @Transactional
    public void likeReview(Long reviewId, boolean liked) {
        UserRef user = currentUser();
        if (reviewMapper.selectById(reviewId) == null) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }

        ReviewLike existing = reviewLikeMapper.selectByReviewAndUser(reviewId, user.type, user.id);
        if (liked) {
            if (existing == null) {
                try {
                    reviewLikeMapper.insert(ReviewLike.builder()
                            .reviewId(reviewId).userType(user.type).userId(user.id).build());
                    reviewMapper.incrementLikeCount(reviewId);
                } catch (DuplicateKeyException e) {
                    // 并发双击：唯一键兜底，计数不再自增
                    log.debug("并发重复点赞评价，已忽略: reviewId={}", reviewId);
                }
            }
        } else if (existing != null) {
            reviewLikeMapper.deleteByReviewAndUser(reviewId, user.type, user.id);
            reviewMapper.decrementLikeCount(reviewId);
        }
        log.info("评价点赞切换: reviewId={}, userType={}, userId={}, liked={}",
                reviewId, user.type, user.id, liked);
    }

    /** 评论回复点赞/取消点赞（可切换，幂等）。 */
    @Override
    @Transactional
    public void likeComment(Long commentId, boolean liked) {
        UserRef user = currentUser();
        if (reviewCommentMapper.selectById(commentId) == null) {
            throw new BusinessException(MessageConstant.REVIEW_COMMENT_NOT_FOUND);
        }

        ReviewCommentLike existing = reviewCommentLikeMapper.selectByCommentAndUser(commentId, user.type, user.id);
        if (liked) {
            if (existing == null) {
                try {
                    reviewCommentLikeMapper.insert(ReviewCommentLike.builder()
                            .commentId(commentId).userType(user.type).userId(user.id).build());
                    reviewCommentMapper.incrementLikeCount(commentId);
                } catch (DuplicateKeyException e) {
                    log.debug("并发重复点赞回复，已忽略: commentId={}", commentId);
                }
            }
        } else if (existing != null) {
            reviewCommentLikeMapper.deleteByCommentAndUser(commentId, user.type, user.id);
            reviewCommentMapper.decrementLikeCount(commentId);
        }
        log.info("回复点赞切换: commentId={}, userType={}, userId={}, liked={}",
                commentId, user.type, user.id, liked);
    }

    /** 当前租客是否已对该房源发表过评论（退租后弹评价用）。 */
    @Override
    public boolean hasReviewed(Long houseId) {
        Long tenantId = BaseContext.getCurrentId();
        if (tenantId == null || houseId == null) {
            return false;
        }
        return reviewMapper.selectByTenantAndHouse(tenantId, houseId) != null;
    }

    /** 删除自己发的评价：级联清理该评价下的全部回复与点赞。 */
    @Override
    @Transactional
    public void deleteReview(Long reviewId) {
        UserRef user = currentUser();
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_FOUND);
        }
        // 评价的作者永远是租客，且只能本人删
        if (!isMine(JwtConstant.TYPE_TENANT, review.getTenantId(), user.id, user.type)) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_OWNER);
        }

        List<Long> commentIds = reviewCommentMapper.selectIdsByReviewId(reviewId);
        if (!commentIds.isEmpty()) {
            reviewCommentLikeMapper.deleteByCommentIds(commentIds);
            reviewCommentMapper.deleteByIds(commentIds);
        }
        reviewLikeMapper.deleteByReviewId(reviewId);
        reviewMapper.deleteById(reviewId);
        log.info("删除评价: reviewId={}, userType={}, userId={}, 级联回复数={}",
                reviewId, user.type, user.id, commentIds.size());
    }

    /** 删除自己发的回复：级联清理其子孙回复与点赞（别人在它下面的追问也会一并消失）。 */
    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        UserRef user = currentUser();
        ReviewComment comment = reviewCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(MessageConstant.REVIEW_COMMENT_NOT_FOUND);
        }
        if (!isMine(comment.getUserType(), comment.getUserId(), user.id, user.type)) {
            throw new BusinessException(MessageConstant.REVIEW_NOT_OWNER);
        }

        // 逐层收集子孙回复：正常只有一两层，加层数上限兜底，防止脏数据形成环时死循环
        List<Long> toDelete = new ArrayList<>();
        toDelete.add(commentId);
        List<Long> frontier = new ArrayList<>();
        frontier.add(commentId);
        for (int depth = 0; depth < 20 && !frontier.isEmpty(); depth++) {
            List<Long> children = new ArrayList<>();
            for (Long pid : frontier) {
                children.addAll(reviewCommentMapper.selectIdsByParentId(pid));
            }
            if (children.isEmpty()) {
                break;
            }
            toDelete.addAll(children);
            frontier = children;
        }

        reviewCommentLikeMapper.deleteByCommentIds(toDelete);
        reviewCommentMapper.deleteByIds(toDelete);
        log.info("删除回复: commentId={}, userType={}, userId={}, 连带子孙数={}",
                commentId, user.type, user.id, toDelete.size() - 1);
    }

    // ==================== 内部工具 ====================

    /** 判断某条内容是否由当前请求者本人发布（未登录 / 身份或 ID 不符一律 false）。 */
    private boolean isMine(String authorType, Long authorId, Long currentId, String currentType) {
        return currentId != null && currentType != null
                && currentType.equals(authorType)
                && currentId.equals(authorId);
    }

    /** 评论内容校验：去空白、非空、限长。 */
    private String validateContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(MessageConstant.REVIEW_CONTENT_EMPTY);
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(MessageConstant.REVIEW_CONTENT_TOO_LONG);
        }
        return text;
    }

    /** 取当前登录身份，未登录直接抛业务异常。 */
    private UserRef currentUser() {
        Long userId = BaseContext.getCurrentId();
        String userType = BaseContext.getCurrentType();
        if (userId == null || userType == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }
        return new UserRef(userType, userId);
    }

    /** 登录身份（类型 + ID）。 */
    private record UserRef(String type, Long id) {
    }

    /** 组装回复 VO：补齐昵称、被回复人昵称、点赞状态、是否本人发布。 */
    private List<ReviewCommentVO> buildCommentVOs(List<ReviewComment> comments,
                                                  Map<Long, ReviewComment> commentById,
                                                  Set<Long> likedCommentIds,
                                                  Map<String, String> userNameCache,
                                                  Long currentId,
                                                  String currentType) {
        List<ReviewCommentVO> vos = new ArrayList<>();
        for (ReviewComment c : comments) {
            ReviewCommentVO vo = new ReviewCommentVO();
            vo.setId(c.getId());
            vo.setReviewId(c.getReviewId());
            vo.setUserType(c.getUserType());
            vo.setUserId(c.getUserId());
            vo.setContent(c.getContent());
            vo.setParentId(c.getParentId());
            vo.setLikeCount(c.getLikeCount() == null ? 0 : c.getLikeCount());
            vo.setCreateTime(c.getCreateTime());
            vo.setUserName(resolveUserName(c.getUserType(), c.getUserId(), userNameCache));
            vo.setLiked(likedCommentIds.contains(c.getId()));
            vo.setMine(isMine(c.getUserType(), c.getUserId(), currentId, currentType));

            // 被回复人昵称：用于前端展示「A 回复 B：」
            ReviewComment parent = c.getParentId() == null ? null : commentById.get(c.getParentId());
            if (parent != null) {
                vo.setParentUserName(resolveUserName(parent.getUserType(), parent.getUserId(), userNameCache));
            }
            vos.add(vo);
        }
        return vos;
    }

    /** 按身份解析昵称，带本地缓存避免同一页内重复查库。 */
    private String resolveUserName(String userType, Long userId, Map<String, String> cache) {
        if (userId == null) {
            return "用户";
        }
        String key = userType + ":" + userId;
        return cache.computeIfAbsent(key, k -> {
            if (JwtConstant.TYPE_LANDLORD.equals(userType)) {
                Landlord landlord = landlordMapper.selectById(userId);
                return landlord != null && landlord.getName() != null ? landlord.getName() : "房东";
            }
            Tenant tenant = tenantMapper.selectById(userId);
            return tenant != null && tenant.getNickname() != null ? tenant.getNickname() : "租客" + userId;
        });
    }

    /** 顶楼帖头像，取不到则为 null（前端降级为文字头像）。 */
    private String resolveTenantAvatar(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        return tenant == null ? null : tenant.getAvatar();
    }
}
