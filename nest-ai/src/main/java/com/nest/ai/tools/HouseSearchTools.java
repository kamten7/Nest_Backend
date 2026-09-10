package com.nest.ai.tools;

import com.nest.common.PageResult;
import com.nest.dto.HouseQueryDTO;
import com.nest.service.HouseService;
import com.nest.vo.HouseMarkerVO;
import com.nest.vo.HouseVO;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.StringJoiner;

/** 房源搜索 AI 工具集 —— 全部只读查询，供 AI 找房助手调用。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HouseSearchTools {

    private final HouseService houseService;

    @Tool("搜索房源。keyword 可以是小区名、地址、标题关键词（如'银帆花园'、'国贸'），city 是城市，district 是区域。用户说'XX附近/XX小区/XX地段'时，把 XX 作为 keyword 搜索")
    public String searchHouses(String keyword,
                               String city,
                               String district,
                               Double minPrice,
                               Double maxPrice,
                               String rentType,
                               Integer roomCount,
                               Integer limit) {
        HouseQueryDTO dto = new HouseQueryDTO();
        dto.setKeyword(keyword);
        dto.setCity(city);
        dto.setDistrict(district);
        dto.setMinPrice(minPrice != null ? BigDecimal.valueOf(minPrice) : null);
        dto.setMaxPrice(maxPrice != null ? BigDecimal.valueOf(maxPrice) : null);
        dto.setRentType(rentType);
        dto.setRoomCount(roomCount);
        dto.setPage(1);
        dto.setPageSize(limit != null && limit > 0 && limit <= 10 ? limit : 5);
        dto.setSortBy("newest");

        PageResult<HouseVO> result = houseService.list(dto);

        if ((result.getRecords() == null || result.getRecords().isEmpty())
                && (dto.getCity() != null || dto.getDistrict() != null)) {
            HouseQueryDTO fallback = new HouseQueryDTO();
            fallback.setKeyword(keyword);
            fallback.setMinPrice(dto.getMinPrice());
            fallback.setMaxPrice(dto.getMaxPrice());
            fallback.setRentType(rentType);
            fallback.setRoomCount(roomCount);
            fallback.setPage(1);
            fallback.setPageSize(dto.getPageSize());
            fallback.setSortBy("newest");
            result = houseService.list(fallback);
        }
        return formatHouses(result.getRecords());
    }

    @Tool("查询单个房源详情，包含描述、户型、朝向、要求等信息")
    public String getHouseDetail(Long houseId) {
        if (houseId == null) return "房源 ID 不能为空";
        try {
            HouseVO vo = houseService.detail(houseId);
            StringJoiner sb = new StringJoiner("\n");
            sb.add("【" + vo.getTitle() + "】");
            sb.add("价格：" + vo.getPrice() + "元/月（押金" + vo.getDeposit() + "元）");
            sb.add("位置：" + vo.getCity() + vo.getDistrict() + vo.getAddress());
            sb.add("户型：" + vo.getRoomCount() + "室" + vo.getHallCount() + "厅" + vo.getBathroomCount() + "卫，"
                    + vo.getArea() + "㎡，" + vo.getOrientation() + "向，" + vo.getRentType());
            if (vo.getTags() != null && !vo.getTags().isEmpty()) {
                sb.add("标签：" + String.join("、", vo.getTags()));
            }
            if (vo.getRequirements() != null && !vo.getRequirements().isBlank()) {
                sb.add("要求：" + vo.getRequirements());
            }
            sb.add("房源ID：" + vo.getId() + "（可用此ID查看详情或预约）");
            return sb.toString();
        } catch (Exception e) {
            log.warn("AI 查询房源详情失败: houseId={}", houseId, e);
            return "未找到该房源";
        }
    }

    @Tool("按经纬度查找附近房源。仅当用户明确给出了坐标或选择了地图位置时才用；用户说'XX小区/XX地名附近'时不要用这个工具，应该用 searchHouses 把地名作为 keyword")
    public String findNearby(Double lat, Double lng, Double radiusMeters) {
        if (lat == null || lng == null) return "需要提供经纬度";
        double radius = radiusMeters != null && radiusMeters > 0 ? radiusMeters : 5000;
        List<HouseMarkerVO> markers = houseService.mapQuery(null, null, null, null, lat, lng, radius);
        if (markers == null || markers.isEmpty()) {
            return "附近 " + Math.round(radius) + " 米内没有房源";
        }
        StringJoiner sb = new StringJoiner("\n");
        sb.add("附近 " + Math.round(radius) + " 米内的房源：");
        for (int i = 0; i < Math.min(markers.size(), 5); i++) {
            HouseMarkerVO m = markers.get(i);
            sb.add((i + 1) + ". " + m.getTitle() + "，¥" + m.getPrice() + "/月（房源ID:" + m.getId() + "）");
        }
        return sb.toString();
    }

    @Tool("查看某房源的住客评论")
    public String getHouseReviews(Long houseId, Integer limit) {
        return "该房源暂无评论（评论功能即将上线）";
    }

    @Tool("根据预算和偏好智能推荐房源")
    public String recommendHouses(Double budget, String city, String preferences, Integer count) {
        HouseQueryDTO dto = new HouseQueryDTO();
        dto.setCity(city);
        dto.setMaxPrice(budget != null ? BigDecimal.valueOf(budget) : null);
        dto.setPage(1);
        dto.setPageSize(count != null && count > 0 && count <= 5 ? count : 3);
        dto.setSortBy("newest");

        PageResult<HouseVO> result = houseService.list(dto);
        List<HouseVO> houses = result.getRecords();
        if (houses == null || houses.isEmpty()) {
            return "没有符合预算" + (budget != null ? budget + "元" : "") + "的房源，建议适当放宽预算";
        }

        StringJoiner sb = new StringJoiner("\n");
        sb.add("为您推荐以下房源：");
        for (int i = 0; i < houses.size(); i++) {
            HouseVO h = houses.get(i);
            sb.add((i + 1) + ". " + h.getTitle() + "，¥" + h.getPrice() + "/月，"
                    + h.getDistrict() + (h.getTags() != null && !h.getTags().isEmpty()
                    ? "，" + String.join("、", h.getTags()) : "")
                    + "（房源ID:" + h.getId() + "）");
        }
        return sb.toString();
    }

    private String formatHouses(List<HouseVO> houses) {
        if (houses == null || houses.isEmpty()) {
            return "没有找到符合条件的房源";
        }
        StringJoiner sb = new StringJoiner("\n");
        for (int i = 0; i < houses.size(); i++) {
            HouseVO h = houses.get(i);
            sb.add((i + 1) + ". " + h.getTitle() + "，¥" + h.getPrice() + "/月，"
                    + (h.getCity() == null ? "" : h.getCity())
                    + (h.getDistrict() == null ? "" : h.getDistrict())
                    + "（房源ID:" + h.getId() + "）");
        }
        sb.add("回复用户时可提及房源ID方便进一步查看详情");
        return sb.toString();
    }
}
