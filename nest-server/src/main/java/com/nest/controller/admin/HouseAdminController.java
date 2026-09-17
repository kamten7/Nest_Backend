package com.nest.controller.admin;

import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.common.Result;
import com.nest.dto.GeocodeDTO;
import com.nest.dto.HouseCreateDTO;
import com.nest.dto.ReverseGeocodeDTO;
import com.nest.service.HouseService;
import com.nest.service.NominatimService;
import com.nest.vo.GeocodeVO;
import com.nest.vo.HouseMarkerVO;
import com.nest.vo.HouseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 房东端房源管理接口。 */
@Slf4j
@RestController
@RequestMapping("/admin/house")
@RequiredArgsConstructor
@Tag(name = "房东端-房源管理", description = "发布、编辑、上下架、删除房源")
public class HouseAdminController {

    private final HouseService houseService;
    private final NominatimService nominatimService;

    /** 上传房源图片到 MinIO。 */
    @PostMapping("/upload")
    @Operation(summary = "上传房源图片")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        log.info("图片上传: name={}, size={}", file.getOriginalFilename(), file.getSize());
        String url = houseService.uploadImage(file);
        return Result.success("上传成功", url);
    }

    /** 发布房源。 */
    @PostMapping
    @Operation(summary = "发布房源")
    public Result<Long> create(@Valid @RequestBody HouseCreateDTO dto) {
        log.info("发布房源: title='{}', city={}, price={}", dto.getTitle(), dto.getCity(), dto.getPrice());
        Long houseId = houseService.create(dto);
        return Result.success("发布成功", houseId);
    }

    /** 编辑房源。 */
    @PutMapping("/{id}")
    @Operation(summary = "编辑房源")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody HouseCreateDTO dto) {
        log.info("编辑房源: id={}", id);
        houseService.update(id, dto);
        return Result.successMsg("编辑成功");
    }

    /** 上架/下架/重新发布。status: 1=上架, 0=下架；在租中(2) 由系统自动管理，不可手动改。 */
    @PutMapping("/{id}/status")
    @Operation(summary = "上架/下架/重新发布")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Parameter(description = "1=上架, 0=下架") @RequestParam Integer status) {
        log.info("房源状态变更: id={}, status={}", id, status);
        houseService.updateStatus(id, status);
        return Result.successMsg(status == 1 ? "已上架" : "已下架");
    }

    /** 删除房源（含关联图片和标签）。 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除房源")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("删除房源: id={}", id);
        houseService.delete(id);
        return Result.successMsg("删除成功");
    }

    /** 我的房源列表（分页）。 */
    @GetMapping("/my")
    @Operation(summary = "我的房源列表")
    public Result<PageResult<HouseVO>> myList(@RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<HouseVO> result = houseService.myList(page, pageSize);
        return Result.success(result);
    }

    /** 房东查看自己的房源详情（编辑回填用，即使下架）。 */
    @GetMapping("/{id}")
    @Operation(summary = "我的房源详情")
    public Result<HouseVO> getById(@PathVariable Long id) {
        log.info("房东查看房源详情: id={}, landlordId={}", id, BaseContext.getCurrentId());
        HouseVO vo = houseService.getOwnedById(id);
        return Result.success(vo);
    }

    /** 房东地图标记点查询。 */
    @GetMapping("/map")
    @Operation(summary = "房东地图标记点")
    public Result<List<HouseMarkerVO>> map() {
        log.info("房东地图标记查询: landlordId={}", BaseContext.getCurrentId());
        List<HouseMarkerVO> markers = houseService.landlordMap();
        return Result.success(markers);
    }

    /** 地址→经纬度（Nominatim 地理编码）。 */
    @PostMapping("/geocode")
    @Operation(summary = "地址地理编码")
    public Result<GeocodeVO> geocode(@Valid @RequestBody GeocodeDTO dto) {
        log.info("地理编码请求: address='{}'", dto.getAddress());
        GeocodeVO result = nominatimService.geocode(dto.getAddress());
        if (result == null) {
            return Result.error("无法解析该地址，请尝试更详细的地址");
        }
        return Result.success("解析成功", result);
    }

    /** 经纬度→地址（反向地理编码，地图选点发布房源用）。 */
    @PostMapping("/geocode/reverse")
    @Operation(summary = "反向地理编码")
    public Result<GeocodeVO> reverseGeocode(@Valid @RequestBody ReverseGeocodeDTO dto) {
        log.info("反向地理编码请求: lat={}, lng={}", dto.getLat(), dto.getLng());
        GeocodeVO result = nominatimService.reverseGeocode(dto.getLat(), dto.getLng());
        if (result == null) {
            return Result.error("无法解析该坐标，请换一个位置");
        }
        return Result.success("解析成功", result);
    }
}
