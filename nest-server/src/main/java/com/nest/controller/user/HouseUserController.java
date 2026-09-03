package com.nest.controller.user;

import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.HouseQueryDTO;
import com.nest.service.HouseService;
import com.nest.vo.HouseMarkerVO;
import com.nest.vo.HouseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租客端房源浏览接口（公开，无需认证）。
 */
@Slf4j
@RestController
@RequestMapping("/user/house")
@RequiredArgsConstructor
@Tag(name = "租客端-房源浏览", description = "浏览房源列表和详情")
public class HouseUserController {

    private final HouseService houseService;

    /**
     * 房源列表（公开，分页+筛选）。
     */
    @GetMapping("/list")
    @Operation(summary = "房源列表", description = "分页浏览上架房源，支持按城市/区域/价格/户型/关键词筛选和排序")
    public Result<PageResult<HouseVO>> list(HouseQueryDTO dto) {
        log.info("房源列表查询: city={}, district={}, minPrice={}, maxPrice={}, page={}",
                dto.getCity(), dto.getDistrict(), dto.getMinPrice(), dto.getMaxPrice(), dto.getPage());
        PageResult<HouseVO> result = houseService.list(dto);
        return Result.success(result);
    }

    /**
     * 房源详情（公开）。
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "房源详情", description = "查看房源完整信息，含图片、标签、房东信息")
    public Result<HouseVO> detail(@PathVariable Long id) {
        log.info("房源详情查询: id={}", id);
        HouseVO vo = houseService.detail(id);
        return Result.success(vo);
    }

    /**
     * 地图标记点查询（公开）。
     * 支持两种模式：bounds（minLat/maxLat/minLng/maxLng）或 center+radius（lat/lng/radius）。
     */
    @GetMapping("/map")
    @Operation(summary = "地图标记点查询", description = "按经纬度范围查询已上架房源标记点。支持 bounds 模式或 center+radius 模式")
    public Result<List<HouseMarkerVO>> map(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radius) {
        log.info("地图标记查询: bounds=[{},{},{},{}], center=({},{}), radius={}",
                minLat, maxLat, minLng, maxLng, lat, lng, radius);
        List<HouseMarkerVO> markers = houseService.mapQuery(minLat, maxLat, minLng, maxLng, lat, lng, radius);
        return Result.success(markers);
    }
}
