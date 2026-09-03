package com.nest.controller.user;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.service.FavoriteService;
import com.nest.vo.FavoriteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 租客端收藏接口。
 */
@Slf4j
@RestController
@RequestMapping("/user/favorite")
@RequiredArgsConstructor
@Tag(name = "租客端-收藏", description = "收藏/取消收藏/我的收藏")
public class FavoriteController {

    private final FavoriteService favoriteService;

    /**
     * 添加收藏。
     */
    @PostMapping
    @Operation(summary = "添加收藏", description = "收藏指定房源")
    public Result<Void> add(@RequestBody Map<String, Long> body) {
        Long houseId = body.get("houseId");
        if (houseId == null) {
            return Result.error("houseId 不能为空");
        }
        log.info("收藏请求: houseId={}", houseId);
        favoriteService.add(houseId);
        return Result.successMsg("收藏成功");
    }

    /**
     * 取消收藏。
     */
    @DeleteMapping("/{houseId}")
    @Operation(summary = "取消收藏", description = "取消收藏指定房源")
    public Result<Void> remove(@PathVariable Long houseId) {
        log.info("取消收藏请求: houseId={}", houseId);
        favoriteService.remove(houseId);
        return Result.successMsg("已取消收藏");
    }

    /**
     * 我的收藏列表。
     */
    @GetMapping("/my")
    @Operation(summary = "我的收藏", description = "分页查询当前租客的收藏列表")
    public Result<PageResult<FavoriteVO>> myList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<FavoriteVO> result = favoriteService.myList(page, pageSize);
        return Result.success(result);
    }

    /**
     * 是否已收藏。
     */
    @GetMapping("/status/{houseId}")
    @Operation(summary = "是否已收藏", description = "判断当前租客是否已收藏某房源")
    public Result<Boolean> status(@PathVariable Long houseId) {
        boolean favorited = favoriteService.hasFavorited(houseId);
        return Result.success(favorited);
    }
}
