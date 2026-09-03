package com.nest.controller.admin;

import com.nest.common.Result;
import com.nest.dto.ReviewCommentDTO;
import com.nest.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 房东端评论接口 —— 房东回复租客评论。
 */
@Slf4j
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
@Tag(name = "房东端-评论", description = "房东回复租客评论")
public class ReviewAdminController {

    private final ReviewService reviewService;

    /**
     * 房东回复评论。
     */
    @PostMapping("/{id}/comment")
    @Operation(summary = "回复评论", description = "房东回复租客评论，支持嵌套")
    public Result<Long> addComment(@PathVariable Long id, @RequestBody ReviewCommentDTO dto) {
        log.info("房东回复评论: reviewId={}", id);
        Long commentId = reviewService.addComment(id, dto.getContent(), dto.getParentId());
        return Result.success("回复成功", commentId);
    }
}
