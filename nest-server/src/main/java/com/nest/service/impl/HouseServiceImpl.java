package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.dto.HouseCreateDTO;
import com.nest.dto.HouseQueryDTO;
import com.nest.entity.House;
import com.nest.entity.HouseImage;
import com.nest.entity.HouseTag;
import com.nest.entity.Landlord;
import com.nest.exception.BusinessException;
import com.nest.exception.NoPermissionException;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.mapper.HouseTagMapper;
import com.nest.mapper.LandlordMapper;
import com.nest.service.HouseService;
import com.nest.service.MinioService;
import com.nest.utils.GeoUtils;
import com.nest.vo.HouseMarkerVO;
import com.nest.vo.HouseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 房源服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseServiceImpl implements HouseService {

    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;
    private final HouseTagMapper houseTagMapper;
    private final LandlordMapper landlordMapper;
    private final MinioService minioService;

    // ==================== 房东端 ====================

    /**
     * 创建房源（房东端）。
     *
     * 流程：插入房源主记录 → 批量插入图片 → 批量插入标签，三步在同一事务内。
     * 当前登录房东 ID 从 {@link BaseContext} 取，不信任前端传入。
     *
     * @param dto 房源创建参数（标题/描述/地址/价格/户型/图片URL列表/标签列表等）
     * @return 新建房源的自增主键 ID
     */
    @Override
    @Transactional
    public Long create(HouseCreateDTO dto) {
        Long landlordId = BaseContext.getCurrentId();   // 从线程上下文取当前登录房东 ID

        // 1. 插入房源
        House house = buildHouse(dto, landlordId);   // DTO → 实体，并绑定房东 ID
        houseMapper.insert(house);   // insert 后 MyBatis 会把自增主键回填到 house.id
        log.info("房源创建: id={}, landlordId={}, title='{}'", house.getId(), landlordId, house.getTitle());

        // 2. 批量插入图片（此时已拿到回填的主键，图片子表依赖它做外键）
        saveImages(house.getId(), dto.getImages());

        // 3. 批量插入标签
        saveTags(house.getId(), dto.getTags());

        return house.getId();   // 返回新建房源 ID
    }

    /**
     * 更新房源（房东端）。
     *
     * 图片和标签采用"先删后插"整体替换策略（不是增量），与前端编辑表单的"完整提交"语义一致。
     *
     * @param houseId 要更新的房源 ID
     * @param dto     新的房源内容（与创建共用 HouseCreateDTO）
     */
    @Override
    @Transactional
    public void update(Long houseId, HouseCreateDTO dto) {
        validateOwnership(houseId);   // 前置校验：房源必须存在且属于当前登录房东

        // 1. 更新房源字段（buildHouse 第二个参数传 null，避免更新时误改归属房东）
        House house = buildHouse(dto, null);
        house.setId(houseId);   // 显式指定要更新的主键
        houseMapper.update(house);   // 动态 SQL：只更新非空字段

        // 2. 替换图片：先删后插（仅当传入图片列表非空时，避免误删原有图）
        if (dto.getImages() != null) {
            houseImageMapper.deleteByHouseId(houseId);   // 删除该房源旧图片
            saveImages(houseId, dto.getImages());       // 重新批量插入新图片
        }

        // 3. 替换标签：先删后插
        if (dto.getTags() != null) {
            houseTagMapper.deleteByHouseId(houseId);   // 删除旧标签
            saveTags(houseId, dto.getTags());          // 重新批量插入新标签
        }

        log.info("房源更新: id={}", houseId);
    }

    /**
     * 上架/下架房源（房东端）。
     *
     * @param houseId 房源 ID
     * @param status  目标状态：1=上架，0=下架
     */
    @Override
    public void updateStatus(Long houseId, Integer status) {
        validateOwnership(houseId);   // 只有房源所有者才能上下架
        houseMapper.updateStatus(houseId, status);   // 只更新 status 字段
        log.info("房源状态变更: id={}, status={}", houseId, status);
    }

    /**
     * 删除房源（房东端）。
     *
     * 先删子表（图片、标签）再删主表，防止数据库孤儿数据。
     * 三个删除在同一事务内，要么全成功要么全回滚。
     *
     * @param houseId 要删除的房源 ID
     */
    @Override
    @Transactional
    public void delete(Long houseId) {
        validateOwnership(houseId);   // 只有房源所有者才能删除
        houseImageMapper.deleteByHouseId(houseId);   // 先删图片子表，避免孤儿数据
        houseTagMapper.deleteByHouseId(houseId);     // 再删标签子表
        houseMapper.deleteById(houseId);             // 最后删房源主表
        log.info("房源删除: id={}", houseId);
    }

    /**
     * 我的房源列表（房东端，分页）。
     *
     * 用 PageHelper 分页插件，startPage 后紧接着的查询自动拼上 limit。
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 当前页房源 VO 列表 + 总条数
     */
    @Override
    public PageResult<HouseVO> myList(Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();   // 取当前登录房东 ID
        PageHelper.startPage(page, pageSize);   // 开启分页，作用于下一条 selectByLandlord
        List<House> houses = houseMapper.selectByLandlord(landlordId);   // 查该房东名下房源
        PageInfo<House> pageInfo = new PageInfo<>(houses);   // 包装分页元数据（total 等）

        // 列表场景 buildVO(h, false)：不加载完整图片列表，只取封面图，省查询
        List<HouseVO> vos = houses.stream()
                .map(h -> buildVO(h, false))
                .collect(Collectors.toList());
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 上传房源图片（房东端）。
     *
     * @param file 上传的图片文件（MultipartFile）
     * @return 上传成功后图片的可访问 URL
     */
    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MessageConstant.IMAGE_UPLOAD_EMPTY);   // 空文件拒绝
        }
        return minioService.upload(file, "house");   // 委托 MinIO 上传，目录前缀 house
    }

    // ==================== 用户端 ====================

    /**
     * 用户端房源列表（公开接口，分页 + 筛选 + 距离）。
     *
     * 支持城市/区域/价格/户型/关键词/排序筛选（见 HouseQueryDTO）；
     * 若用户传了经纬度（userLat/userLng），还会用 Haversine 算距离。
     *
     * @param dto 查询条件（分页参数 + 筛选条件 + 可选用户经纬度）
     * @return 房源 VO 分页结果，VO 含封面、标签、距离文本
     */
    @Override
    public PageResult<HouseVO> list(HouseQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());   // 开启分页
        List<House> houses = houseMapper.selectByCondition(dto);   // 按动态 SQL 条件查询
        PageInfo<House> pageInfo = new PageInfo<>(houses);   // 分页元数据

        if (houses.isEmpty()) {
            return PageResult.of(0L, Collections.emptyList());   // 无结果直接返回空页
        }

        // 批量查询标签：一次 IN 查询拿到本页所有房源的全部标签，避免逐条查（N+1 问题）
        List<Long> houseIds = houses.stream().map(House::getId).toList();   // 收集本页房源 ID
        Map<Long, List<String>> tagMap = houseTagMapper.selectByHouseIds(houseIds).stream()
                .collect(Collectors.groupingBy(   // 按 houseId 分组，聚合出每个房源的标签名列表
                        HouseTag::getHouseId,
                        Collectors.mapping(HouseTag::getTagName, Collectors.toList())));

        // 逐个房源转 VO 并补标签、距离
        List<HouseVO> vos = houses.stream().map(h -> {
            HouseVO vo = buildVO(h, false);   // 基础字段转 VO
            vo.setTags(tagMap.getOrDefault(h.getId(), Collections.emptyList()));   // 补标签，无标签给空列表
            // 距离计算：用户和房源都有坐标时才算
            if (dto.getUserLat() != null && dto.getUserLng() != null && h.getLatitude() != null && h.getLongitude() != null) {
                double meters = GeoUtils.distance(   // Haversine 算直线距离（米）
                        dto.getUserLat(), dto.getUserLng(),
                        h.getLatitude(), h.getLongitude());
                vo.setDistanceText(GeoUtils.formatDistance(meters));   // 格式化为 "500m" / "1.2km"
            }
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 房源详情（用户端，公开）。
     *
     * 只允许查看已上架的房源；每次访问浏览量 +1。
     * 返回完整信息：基础字段 + 图片列表 + 标签列表 + 房东名称/头像。
     *
     * @param houseId 房源 ID
     * @return 完整房源 VO（含图片、标签、房东信息）
     */
    @Override
    public HouseVO detail(Long houseId) {
        House house = houseMapper.selectById(houseId);   // 按主键查房源
        if (house == null || house.getStatus() == 0) {   // 不存在或已下架（status=0）都不可查看
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }

        // 浏览量 +1（用独立 SQL 自增，避免整行更新、减少锁开销）
        houseMapper.incrementViewCount(houseId);

        HouseVO vo = buildVO(house, true);   // 详情场景 buildVO(h, true)

        // 加载图片列表：查该房源全部图片，只取 URL 给前端
        List<HouseImage> images = houseImageMapper.selectByHouseId(houseId);
        vo.setImages(images.stream().map(HouseImage::getUrl).collect(Collectors.toList()));

        // 加载标签：查该房源全部标签名
        List<HouseTag> tags = houseTagMapper.selectByHouseId(houseId);
        vo.setTags(tags.stream().map(HouseTag::getTagName).collect(Collectors.toList()));

        // 加载房东信息：查房源所属房东，填充名称/头像
        Landlord landlord = landlordMapper.selectById(house.getLandlordId());
        if (landlord != null) {   // 房东可能被删，判空兜底
            vo.setLandlordName(landlord.getName());
            vo.setLandlordAvatar(landlord.getAvatar());
        }

        return vo;
    }

    /**
     * 房东查看自己的房源详情（编辑回填用）。
     *
     * 与 {@link #detail} 的区别：不过滤已下架状态（status=0），因为房东要能编辑下架中的房源；
     * 但必须校验归属——只能看自己名下的房源。
     *
     * @param houseId 房源 ID
     * @return 完整房源 VO（含图片、标签、房东信息）
     */
    @Override
    public HouseVO getOwnedById(Long houseId) {
        // 房东查看自己的房源详情（编辑回填用，不走 detail 的 status 拦截）
        House house = houseMapper.selectById(houseId);   // 按主键查房源
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        // 校验所有权：房源归属 != 当前登录房东 → 无权限
        if (!house.getLandlordId().equals(BaseContext.getCurrentId())) {
            throw new NoPermissionException(MessageConstant.HOUSE_NOT_OWNER);
        }

        HouseVO vo = buildVO(house, true);   // 详情场景转换

        // 加载图片列表（编辑表单回填用）
        List<HouseImage> images = houseImageMapper.selectByHouseId(houseId);
        vo.setImages(images.stream().map(HouseImage::getUrl).collect(Collectors.toList()));

        // 加载标签（编辑表单回填用）
        List<HouseTag> tags = houseTagMapper.selectByHouseId(houseId);
        vo.setTags(tags.stream().map(HouseTag::getTagName).collect(Collectors.toList()));

        // 加载房东信息（详情展示用）
        Landlord landlord = landlordMapper.selectById(house.getLandlordId());
        if (landlord != null) {   // 判空兜底
            vo.setLandlordName(landlord.getName());
            vo.setLandlordAvatar(landlord.getAvatar());
        }

        return vo;
    }

    // ==================== 地图 ====================

    /**
     * 地图房源标记查询（用户端/房东端共用）。
     *
     * 支持两种入参模式：
     * <ul>
     *   <li>中心点+半径：传 {@code lat, lng, radius}，先用 {@code boundingBox} 把圆形范围换算成矩形</li>
     *   <li>直接给矩形：传 {@code minLat, maxLat, minLng, maxLng}</li>
     * </ul>
     * 参数缺失或非法（经纬度越界）抛 {@code MAP_PARAM_INVALID}。
     *
     * @param minLat 矩形南边界（或中心点模式下为 null）
     * @param maxLat 矩形北边界（或中心点模式下为 null）
     * @param minLng 矩形西边界（或中心点模式下为 null）
     * @param maxLng 矩形东边界（或中心点模式下为 null）
     * @param lat    中心点纬度（可选）
     * @param lng    中心点经度（可选）
     * @param radius 中心点半径（米，可选）
     * @return 落在矩形范围内的房源标记点列表
     */
    @Override
    public List<com.nest.vo.HouseMarkerVO> mapQuery(Double minLat, Double maxLat,
                                                      Double minLng, Double maxLng,
                                                      Double lat, Double lng, Double radius) {
        // 模式 1：center + radius → 转 bounds
        if (lat != null && lng != null && radius != null) {
            // 圆形范围不好在 SQL 里查，用外接矩形近似（粗筛）
            double[] box = GeoUtils.boundingBox(lat, lng, radius);
            minLat = box[0];   // 矩形南边界
            maxLat = box[1];   // 矩形北边界
            minLng = box[2];   // 矩形西边界
            maxLng = box[3];   // 矩形东边界
        }

        // 模式 2：直接 bounds（此时 4 个边界值必须齐全）
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            throw new com.nest.exception.BusinessException(com.nest.constant.MessageConstant.MAP_PARAM_INVALID);
        }

        // 校验范围：经纬度必须在合法区间，防止异常查询
        if (minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180) {
            throw new com.nest.exception.BusinessException(com.nest.constant.MessageConstant.MAP_PARAM_INVALID);
        }

        return houseMapper.selectByBounds(minLat, maxLat, minLng, maxLng);   // 矩形范围粗筛（SQL BETWEEN）
    }

    /**
     * 房东的地图标记查询：返回当前登录房东名下所有房源的标记点。
     *
     * @return 房东名下房源的标记点列表（含标题、价格、坐标、封面）
     */
    @Override
    public List<HouseMarkerVO> landlordMap() {
        Long landlordId = BaseContext.getCurrentId();   // 取当前登录房东 ID
        return houseMapper.selectByLandlordMap(landlordId);   // 查该房东名下所有房源标记
    }


    // ==================== 内部方法 ====================

    /** 校验房源所有权 */
    private void validateOwnership(Long houseId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        if (!house.getLandlordId().equals(BaseContext.getCurrentId())) {
            throw new NoPermissionException(MessageConstant.HOUSE_NOT_OWNER);
        }
    }

    /** DTO → House 实体（landlordId 为 null 时跳过） */
    private House buildHouse(HouseCreateDTO dto, Long landlordId) {
        House h = new House();
        h.setLandlordId(landlordId);
        h.setTitle(dto.getTitle());
        h.setDescription(dto.getDescription());
        h.setAddress(dto.getAddress());
        h.setProvince(dto.getProvince());
        h.setCity(dto.getCity());
        h.setDistrict(dto.getDistrict());
        h.setLatitude(dto.getLatitude());
        h.setLongitude(dto.getLongitude());
        h.setPrice(dto.getPrice());
        h.setDeposit(dto.getDeposit());
        h.setArea(dto.getArea());
        h.setRoomCount(dto.getRoomCount());
        h.setHallCount(dto.getHallCount());
        h.setBathroomCount(dto.getBathroomCount());
        h.setFloor(dto.getFloor());
        h.setTotalFloor(dto.getTotalFloor());
        h.setOrientation(dto.getOrientation());
        h.setRentType(dto.getRentType());
        h.setAvailableDate(dto.getAvailableDate());
        h.setUtilities(dto.getUtilities());
        h.setRequirements(dto.getRequirements());
        return h;
    }

    /** House 实体 → HouseVO */
    private HouseVO buildVO(House h, boolean isDetail) {
        HouseVO vo = new HouseVO();
        vo.setId(h.getId());
        vo.setLandlordId(h.getLandlordId());
        vo.setTitle(h.getTitle());
        vo.setDescription(h.getDescription());
        vo.setAddress(h.getAddress());
        vo.setProvince(h.getProvince());
        vo.setCity(h.getCity());
        vo.setDistrict(h.getDistrict());
        vo.setLatitude(h.getLatitude());
        vo.setLongitude(h.getLongitude());
        vo.setPrice(h.getPrice());
        vo.setDeposit(h.getDeposit());
        vo.setArea(h.getArea());
        vo.setRoomCount(h.getRoomCount());
        vo.setHallCount(h.getHallCount());
        vo.setBathroomCount(h.getBathroomCount());
        vo.setFloor(h.getFloor());
        vo.setTotalFloor(h.getTotalFloor());
        vo.setOrientation(h.getOrientation());
        vo.setRentType(h.getRentType());
        vo.setAvailableDate(h.getAvailableDate());
        vo.setUtilities(h.getUtilities());
        vo.setRequirements(h.getRequirements());
        vo.setStatus(h.getStatus());
        vo.setViewCount(h.getViewCount());
        vo.setCreateTime(h.getCreateTime());

        // 封面图
        List<HouseImage> images = houseImageMapper.selectByHouseId(h.getId());
        if (images != null && !images.isEmpty()) {
            vo.setCoverImage(images.get(0).getUrl());
        }

        return vo;
    }

    /** 批量保存图片 */
    private void saveImages(Long houseId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        List<HouseImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(HouseImage.builder()
                    .houseId(houseId)
                    .url(imageUrls.get(i))
                    .isCover(i == 0 ? 1 : 0)
                    .sortOrder(i)
                    .build());
        }
        houseImageMapper.insertBatch(images);
    }

    /** 批量保存标签 */
    private void saveTags(Long houseId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;
        List<HouseTag> tags = tagNames.stream()
                .map(name -> HouseTag.builder().houseId(houseId).tagName(name).build())
                .collect(Collectors.toList());
        houseTagMapper.insertBatch(tags);
    }
}
