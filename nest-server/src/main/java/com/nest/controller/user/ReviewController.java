package com.nest.controller.user;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.ReviewCommentDTO;
import com.nest.dto.ReviewCreateDTO;
import com.nest.service.ReviewService;
import com.nest.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评论接口（租客发表/浏览，租客或房东回复，点赞）。
 */
@Slf4j
@RestController
@RequestMapping("/user/review")
@RequiredArgsConstructor
@Tag(name = "评论", description = "发表评论/评论列表/回复/点赞")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 发表评论（租客，一房一评）。
     */
    @PostMapping
    @Operation(summary = "发表评论", description = "租客对房源评分(1-5)+文字评论，每个租客每房源限评一次")
    public Result<Long> create(@RequestBody ReviewCreateDTO dto) {
        log.info("发表评论: houseId={}, rating={}", dto.getHouseId(), dto.getRating());
        Long id = reviewService.createReview(dto.getHouseId(), dto.getRating(), dto.getContent());
        return Result.success("评价成功", id);
    }

    /**
     * 房源评论列表（公开，含回复）。
     */
    @GetMapping("/house/{houseId}")
    @Operation(summary = "房源评论列表", description = "查看房源评论（含回复、平均分、评论数），公开接口")
    public Result<PageResult<ReviewVO>> listByHouse(
            @PathVariable Long houseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("房源评论列表: houseId={}", houseId);
        PageResult<ReviewVO> result = reviewService.listByHouse(houseId, page, pageSize);
        return Result.success(result);
    }

    /**
     * 回复评论（租客或房东）。
     */
    @PostMapping("/{id}/comment")
    @Operation(summary = "回复评论", description = "租客或房东回复评论，支持嵌套")
    public Result<Long> addComment(@PathVariable Long id, @RequestBody ReviewCommentDTO dto) {
        log.info("回复评论: reviewId={}", id);
        Long commentId = reviewService.addComment(id, dto.getContent(), dto.getParentId());
        return Result.success("回复成功", commentId);
    }

    /**
     * 点赞/取消点赞（可切换）。
     */
    @PostMapping("/comment/{id}/like")
    @Operation(summary = "点赞/取消赞", description = "body 传 liked: true 点赞 / false 取消")
    public Result<Void> like(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean liked = body != null && Boolean.TRUE.equals(body.get("liked"));
        log.info("评论点赞切换: commentId={}, liked={}", id, liked);
        reviewService.likeComment(id, liked);
        return Result.successMsg(liked ? "点赞成功" : "已取消点赞");
    }
}
