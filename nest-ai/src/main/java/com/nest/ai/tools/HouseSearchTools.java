package com.nest.ai.tools;

import com.nest.common.PageResult;
import com.nest.dto.HouseQueryDTO;
import com.nest.service.HouseService;
import com.nest.vo.HouseMarkerVO;
import com.nest.vo.HouseVO;
import dev.langchain4j.agent.tool.P;
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

    @Tool("搜索房源。城市放 city（如'湛江市'），区域放 district（如'霞山区'），keyword 只放小区名/地址/标题关键词（如'银帆花园'）。不要把区域名、城市名或整句话塞进 keyword")
    public String searchHouses(
            @P("关键词：只放小区名/地址/标题词，如'银帆花园'、'国贸'；不知道就不传，不要传区域名、城市名或整句话")
            String keyword,
            @P("城市，如'湛江市'；不知道就不传，不要编造城市")
            String city,
            @P("区域/区县，如'霞山区'；不知道就不传")
            String district,
            @P("最低月租（元）；未知不传")
            Double minPrice,
            @P("最高月租（元）；未知不传")
            Double maxPrice,
            @P("出租方式：整租 / 合租 / 短租；未知不传")
            String rentType,
            @P("户型室数：一居室=1、二居室=2、三居室=3；未知不传")
            Integer roomCount,
            @P("返回条数，1-10，默认 5")
            Integer limit) {
        int size = limit != null && limit > 0 && limit <= 10 ? limit : 5;

        PageResult<HouseVO> result = houseService.list(
                buildQuery(keyword, city, district, minPrice, maxPrice, rentType, roomCount, size));
        String relaxed = "无";

        // 分级放宽（按"误伤概率"从高到低放开）。
        // ⚠️ 原实现只丢 city/district 却保留 keyword —— 而 keyword 只在 title/description 上 LIKE，
        // 一旦模型把地名/整句塞进 keyword（如 keyword='霞山区'、'湛江市内二居室'），
        // 两轮都会查空、永远救不回来。所以第一级必须先把 keyword 丢掉。
        if (isEmpty(result) && notBlank(keyword)) {
            result = houseService.list(
                    buildQuery(null, city, district, minPrice, maxPrice, rentType, roomCount, size));
            relaxed = "已忽略 keyword";
        }
        if (isEmpty(result) && (notBlank(city) || notBlank(district))) {
            result = houseService.list(
                    buildQuery(null, null, null, minPrice, maxPrice, rentType, roomCount, size));
            relaxed = "已忽略 keyword + 城市/区域";
        }

        log.info("AI 搜索房源: keyword={}, city={}, district={}, roomCount={}, rentType={}, price=[{}, {}], limit={}"
                        + " → 命中 {} 条（放宽: {}）",
                keyword, city, district, roomCount, rentType, minPrice, maxPrice, size,
                result.getRecords() == null ? 0 : result.getRecords().size(), relaxed);

        return formatHouses(result.getRecords());
    }

    /** 组装查询条件（空串统一转 null，避免空串参与过滤）。 */
    private HouseQueryDTO buildQuery(String keyword, String city, String district,
                                     Double minPrice, Double maxPrice, String rentType,
                                     Integer roomCount, int pageSize) {
        HouseQueryDTO dto = new HouseQueryDTO();
        dto.setKeyword(blankToNull(keyword));
        dto.setCity(blankToNull(city));
        dto.setDistrict(blankToNull(district));
        dto.setMinPrice(minPrice != null ? BigDecimal.valueOf(minPrice) : null);
        dto.setMaxPrice(maxPrice != null ? BigDecimal.valueOf(maxPrice) : null);
        dto.setRentType(blankToNull(rentType));
        dto.setRoomCount(roomCount);
        dto.setPage(1);
        dto.setPageSize(pageSize);
        dto.setSortBy("newest");
        return dto;
    }

    private static boolean isEmpty(PageResult<HouseVO> result) {
        return result == null || result.getRecords() == null || result.getRecords().isEmpty();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
