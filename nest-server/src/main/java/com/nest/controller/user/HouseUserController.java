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

/** 租客端房源浏览接口（公开，无需认证）。 */
@Slf4j
@RestController
@RequestMapping("/user/house")
@RequiredArgsConstructor
@Tag(name = "租客端-房源浏览", description = "浏览房源列表和详情")
public class HouseUserController {

    private final HouseService houseService;

    /** 房源列表（公开，分页+筛选）。 */
    @GetMapping("/list")
    @Operation(summary = "房源列表")
    public Result<PageResult<HouseVO>> list(HouseQueryDTO dto) {
        log.info("房源列表查询: city={}, district={}, page={}", dto.getCity(), dto.getDistrict(), dto.getPage());
        PageResult<HouseVO> result = houseService.list(dto);
        return Result.success(result);
    }

    /** 房源详情（公开）。 */
    @GetMapping("/detail/{id}")
    @Operation(summary = "房源详情")
    public Result<HouseVO> detail(@PathVariable Long id) {
        log.info("房源详情查询: id={}", id);
        HouseVO vo = houseService.detail(id);
        return Result.success(vo);
    }

    /** 地图标记点查询（公开）。支持 bounds 或 center+radius 模式。 */
    @GetMapping("/map")
    @Operation(summary = "地图标记点查询")
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
