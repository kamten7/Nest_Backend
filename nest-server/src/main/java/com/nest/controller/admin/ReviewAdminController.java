package com.nest.controller.admin;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.ReviewCommentDTO;
import com.nest.service.ReviewService;
import com.nest.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 房东端评论接口 —— 房东查看评论、回复、点赞、删除自己的回复。
 */
@Slf4j
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
@Tag(name = "房东端-评论", description = "房东回复租客评论")
public class ReviewAdminController {

    private final ReviewService reviewService;

    /**
     * 房源评论列表（房东视角）。
     * 走 /admin 通道 ⇒ BaseContext 里是 landlord，因此返回的 liked / mine 都是房东本人的状态。
     */
    @GetMapping("/house/{houseId}")
    @Operation(summary = "房源评论列表(房东视角)", description = "含回复、平均分、点赞数，以及房东本人的 liked / mine")
    public Result<PageResult<ReviewVO>> listByHouse(
            @PathVariable Long houseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer pageSize) {
        log.info("房东端房源评论列表: houseId={}", houseId);
        return Result.success(reviewService.listByHouse(houseId, page, pageSize));
    }

    /**
     * 点赞/取消点赞顶楼评价（房东也可点赞）。
     */
    @PostMapping("/{id}/like")
    @Operation(summary = "评价点赞/取消赞", description = "body 传 liked: true 点赞 / false 取消")
    public Result<Void> likeReview(@PathVariable Long id, @RequestBody(required = false) Map<String, Boolean> body) {
        boolean liked = body != null && Boolean.TRUE.equals(body.get("liked"));
        log.info("房东端评价点赞切换: reviewId={}, liked={}", id, liked);
        reviewService.likeReview(id, liked);
        return Result.successMsg(liked ? "点赞成功" : "已取消点赞");
    }

    /**
     * 点赞/取消点赞回复（房东也可点赞）。
     */
    @PostMapping("/comment/{id}/like")
    @Operation(summary = "回复点赞/取消赞", description = "body 传 liked: true 点赞 / false 取消")
    public Result<Void> likeComment(@PathVariable Long id, @RequestBody(required = false) Map<String, Boolean> body) {
        boolean liked = body != null && Boolean.TRUE.equals(body.get("liked"));
        log.info("房东端回复点赞切换: commentId={}, liked={}", id, liked);
        reviewService.likeComment(id, liked);
        return Result.successMsg(liked ? "点赞成功" : "已取消点赞");
    }

    /**
     * 删除自己发的回复（房东只能删自己的）。
     */
    @DeleteMapping("/comment/{id}")
    @Operation(summary = "删除回复", description = "只能删除本人发表的回复，其下追问与点赞一并删除")
    public Result<Void> deleteComment(@PathVariable Long id) {
        log.info("房东端删除回复: commentId={}", id);
        reviewService.deleteComment(id);
        return Result.successMsg("已删除");
    }

    /**
     * 房东回复评论。
     */
    @PostMapping("/{id}/comment")
    @Operation(summary = "回复评论", description = "房东回复租客评论，支持嵌套")
    public Result<Long> addComment(@PathVariable Long id, @Valid @RequestBody ReviewCommentDTO dto) {
        log.info("房东回复评论: reviewId={}", id);
        Long commentId = reviewService.addComment(id, dto.getContent(), dto.getParentId());
        return Result.success("回复成功", commentId);
    }
}
