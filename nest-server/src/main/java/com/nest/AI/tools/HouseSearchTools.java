package com.nest.AI.tools;

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

/**
 * 房源搜索 AI 工具集 —— 全部只读查询，供 AI 找房助手调用。
 *
 * 设计要点：
 *
 * 所有 @Tool 方法返回 字符串文本（简洁摘要），不是对象。
 * 因为 LLM 只理解文本，且避免对象序列化问题。
 * 只读不写，无并发串号风险（不存任何实例字段）。
 * 写操作（预约/收藏）的工具留到后续阶段，需配合确认机制。
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HouseSearchTools {

    private final HouseService houseService;

    /**
     * 按条件搜索房源。
     *
     * @param keyword   关键词（标题/描述/地址），如 '银帆花园' '朝阳区国贸'
     * @param city      城市，如 '湛江市'
     * @param district  区域，如 '霞山区'
     * @param minPrice  最低月租金
     * @param maxPrice  最高月租金
     * @param rentType  出租方式：整租/合租/短租
     * @param roomCount 室数（户型）
     * @param limit     返回条数上限
     * @return 房源摘要列表文本，如 "1|朝阳精装两居室|3500元/月|湛江市霞山区"
     */
    @Tool("搜索房源。keyword 可以是小区名、地址、标题关键词（如'银帆花园'、'国贸'），city 是城市，district 是区域。用户说'XX附近/XX小区/XX地段'时，把 XX 作为 keyword 搜索")
    public String searchHouses(String keyword,
                               String city,
                               String district,
                               Double minPrice,
                               Double maxPrice,
                               String rentType,
                               Integer roomCount,
                               Integer limit) {
        // 组装查询条件：把 LLM 传来的参数映射到后端 DTO
        HouseQueryDTO dto = new HouseQueryDTO();
        dto.setKeyword(keyword);                 // 关键词：匹配标题/描述/地址
        dto.setCity(city);                       // 城市（AI 可能传空，表示不限）
        dto.setDistrict(district);               // 区域
        dto.setMinPrice(minPrice != null ? BigDecimal.valueOf(minPrice) : null);   // 最低价，null=不限
        dto.setMaxPrice(maxPrice != null ? BigDecimal.valueOf(maxPrice) : null);   // 最高价，null=不限
        dto.setRentType(rentType);               // 出租方式
        dto.setRoomCount(roomCount);             // 室数
        dto.setPage(1);                          // 固定第一页（工具场景不分页）
        dto.setPageSize(limit != null && limit > 0 && limit <= 10 ? limit : 5);   // 默认 5 条，最多 10，防返回过长文本
        dto.setSortBy("newest");                 // 按最新排序，结果稳定

        PageResult<HouseVO> result = houseService.list(dto);

        // 兜底：如果带了 city/district 但没结果，可能是 AI 猜错了城市（如把湛江猜成杭州），去掉城市条件再查一次
        if ((result.getRecords() == null || result.getRecords().isEmpty())
                && (dto.getCity() != null || dto.getDistrict() != null)) {
            HouseQueryDTO fallback = new HouseQueryDTO();
            fallback.setKeyword(keyword);         // 保留关键词（小区名等）
            fallback.setMinPrice(dto.getMinPrice());
            fallback.setMaxPrice(dto.getMaxPrice());
            fallback.setRentType(rentType);
            fallback.setRoomCount(roomCount);
            fallback.setPage(1);
            fallback.setPageSize(dto.getPageSize());
            fallback.setSortBy("newest");
            // 去掉 city/district 重新查询
            result = houseService.list(fallback);
        }
        return formatHouses(result.getRecords());   // 转成 LLM 易读的文本摘要
    }

    /**
     * 查询单个房源详情。
     *
     * @param houseId 房源 ID
     * @return 房源完整信息文本
     */
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

    /**
     * 查找附近房源。
     *
     * @param lat         用户纬度
     * @param lng         用户经度
     * @param radiusMeters 搜索半径（米）
     * @return 附近房源摘要列表
     */
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

    /**
     * 查看房源评论（阶段 7 前为占位）。
     */
    @Tool("查看某房源的住客评论")
    public String getHouseReviews(Long houseId, Integer limit) {
        // 阶段 7 评论系统未实现，先返回占位，避免 LLM 捏造评论
        return "该房源暂无评论（评论功能即将上线）";
    }

    /**
     * 智能推荐房源。
     *
     * @param budget      预算上限（元/月）
     * @param city        城市
     * @param preferences 偏好描述，如 '近地铁、朝南'
     * @param count       推荐数量
     * @return 推荐房源摘要列表
     */
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

    // ==================== 内部工具 ====================

    /** 房源列表 → 摘要文本 */
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
