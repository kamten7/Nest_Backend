package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageResult;
import com.nest.constant.HouseStatus;
import com.nest.constant.JwtConstant;
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
import com.nest.minio.service.MinioService;
import com.nest.order.mapper.RentOrderMapper;
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

/** 房源服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseServiceImpl implements HouseService {

    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;
    private final HouseTagMapper houseTagMapper;
    private final LandlordMapper landlordMapper;
    private final MinioService minioService;
    private final RentOrderMapper rentOrderMapper;

    /** 创建房源（房东端）。 */
    @Override
    @Transactional
    public Long create(HouseCreateDTO dto) {
        Long landlordId = BaseContext.getCurrentId();

        House house = buildHouse(dto, landlordId);
        houseMapper.insert(house);
        log.info("房源创建: id={}, landlordId={}, title='{}'", house.getId(), landlordId, house.getTitle());

        saveImages(house.getId(), dto.getImages());
        saveTags(house.getId(), dto.getTags());

        return house.getId();
    }

    /** 更新房源（房东端）。图片和标签采用"先删后插"整体替换策略。 */
    @Override
    @Transactional
    public void update(Long houseId, HouseCreateDTO dto) {
        validateOwnership(houseId);

        House house = buildHouse(dto, null);
        house.setId(houseId);
        houseMapper.update(house);

        if (dto.getImages() != null) {
            houseImageMapper.deleteByHouseId(houseId);
            saveImages(houseId, dto.getImages());
        }

        if (dto.getTags() != null) {
            houseTagMapper.deleteByHouseId(houseId);
            saveTags(houseId, dto.getTags());
        }

        log.info("房源更新: id={}", houseId);
    }

    /** 上下架/重新发布（房东端）。status: 1=上架, 0=下架；在租中(2) 不允许手动改。 */
    @Override
    public void updateStatus(Long houseId, Integer status) {
        validateOwnership(houseId);
        House cur = houseMapper.selectById(houseId);
        if (cur != null && cur.getStatus() != null && cur.getStatus() == HouseStatus.RENTED) {
            throw new BusinessException(MessageConstant.HOUSE_RENTED_NO_MANUAL);
        }
        houseMapper.updateStatus(houseId, status);
        log.info("房源状态变更: id={}, status={}", houseId, status);
    }

    /**
     * 删除房源（房东端）。先删子表再删主表，同一事务内。
     *
     * <p><b>在租中的房源不允许删除</b>：只要房源处于「在租中」，或名下还有未终结的订单
     * （待缴押金 1 / 租房中 2 / 退租申请中 3），一律拒绝。否则 {@code rent_order} 会指向一个
     * 不存在的 house —— 订单详情/列表的标题与封面变 null，而押金锁定金额仍按订单状态 2/3
     * 计算，出现「钱还锁着、房子却没了」的脱节状态，且租客侧无法自行恢复。
     * 房东想删房必须先走完退租（或让租客放弃租房）。
     */
    @Override
    @Transactional
    public void delete(Long houseId) {
        validateOwnership(houseId);

        House cur = houseMapper.selectById(houseId);
        boolean renting = cur != null && cur.getStatus() != null
                && cur.getStatus() == HouseStatus.RENTED;
        int activeOrders = rentOrderMapper.countActiveByHouse(houseId);
        if (renting || activeOrders > 0) {
            log.warn("拒绝删除房源（存在未终结的租赁关系）: id={}, status={}, activeOrders={}",
                    houseId, cur == null ? null : cur.getStatus(), activeOrders);
            throw new BusinessException(MessageConstant.HOUSE_RENTED_CANNOT_DELETE);
        }

        houseImageMapper.deleteByHouseId(houseId);
        houseTagMapper.deleteByHouseId(houseId);
        houseMapper.deleteById(houseId);
        log.info("房源删除: id={}", houseId);
    }

    /** 我的房源列表（房东端，分页）。 */
    @Override
    public PageResult<HouseVO> myList(Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();
        PageHelper.startPage(page, pageSize);
        List<House> houses = houseMapper.selectByLandlord(landlordId);
        PageInfo<House> pageInfo = new PageInfo<>(houses);

        List<HouseVO> vos = houses.stream()
                .map(h -> buildVO(h, false))
                .collect(Collectors.toList());
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 上传房源图片（房东端）。 */
    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MessageConstant.IMAGE_UPLOAD_EMPTY);
        }
        return minioService.upload(file, "house");
    }

    /** 用户端房源列表（公开，分页+筛选+距离）。 */
    @Override
    public PageResult<HouseVO> list(HouseQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        List<House> houses = houseMapper.selectByCondition(dto);
        PageInfo<House> pageInfo = new PageInfo<>(houses);

        if (houses.isEmpty()) {
            return PageResult.of(0L, Collections.emptyList());
        }

        List<Long> houseIds = houses.stream().map(House::getId).toList();
        Map<Long, List<String>> tagMap = houseTagMapper.selectByHouseIds(houseIds).stream()
                .collect(Collectors.groupingBy(HouseTag::getHouseId,
                        Collectors.mapping(HouseTag::getTagName, Collectors.toList())));

        List<HouseVO> vos = houses.stream().map(h -> {
            HouseVO vo = buildVO(h, false);
            vo.setTags(tagMap.getOrDefault(h.getId(), Collections.emptyList()));
            if (dto.getUserLat() != null && dto.getUserLng() != null
                    && h.getLatitude() != null && h.getLongitude() != null) {
                double meters = GeoUtils.distance(
                        dto.getUserLat(), dto.getUserLng(),
                        h.getLatitude(), h.getLongitude());
                vo.setDistanceText(GeoUtils.formatDistance(meters));
            }
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /**
     * 房源详情（用户端，可选认证）。
     *
     * <p>默认只允许查看已上架房源；但**租过这套房的租客（含已退租）可以回看**——
     * 退租结算会把房源打回「下架」，若不放开，退租租客就再也进不来房源页看评论、追评了。
     */
    @Override
    public HouseVO detail(Long houseId) {
        House house = houseMapper.selectById(houseId);
        Long currentId = BaseContext.getCurrentId();
        boolean rentedByMe = currentId != null
                && JwtConstant.TYPE_TENANT.equals(BaseContext.getCurrentType())
                && rentOrderMapper.countByTenantAndHouse(currentId, houseId) > 0;

        if (house == null || (house.getStatus() != 1 && !rentedByMe)) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }

        houseMapper.incrementViewCount(houseId);
        HouseVO vo = buildVO(house, true);

        List<HouseImage> images = houseImageMapper.selectByHouseId(houseId);
        vo.setImages(images.stream().map(HouseImage::getUrl).collect(Collectors.toList()));

        List<HouseTag> tags = houseTagMapper.selectByHouseId(houseId);
        vo.setTags(tags.stream().map(HouseTag::getTagName).collect(Collectors.toList()));

        Landlord landlord = landlordMapper.selectById(house.getLandlordId());
        if (landlord != null) {
            vo.setLandlordName(landlord.getName());
            vo.setLandlordAvatar(landlord.getAvatar());
        }

        return vo;
    }

    /** 房东查看自己的房源详情（编辑回填用，不过滤下架状态，但校验归属）。 */
    @Override
    public HouseVO getOwnedById(Long houseId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        if (!house.getLandlordId().equals(BaseContext.getCurrentId())) {
            throw new NoPermissionException(MessageConstant.HOUSE_NOT_OWNER);
        }

        HouseVO vo = buildVO(house, true);

        List<HouseImage> images = houseImageMapper.selectByHouseId(houseId);
        vo.setImages(images.stream().map(HouseImage::getUrl).collect(Collectors.toList()));

        List<HouseTag> tags = houseTagMapper.selectByHouseId(houseId);
        vo.setTags(tags.stream().map(HouseTag::getTagName).collect(Collectors.toList()));

        Landlord landlord = landlordMapper.selectById(house.getLandlordId());
        if (landlord != null) {
            vo.setLandlordName(landlord.getName());
            vo.setLandlordAvatar(landlord.getAvatar());
        }

        return vo;
    }

    /** 地图房源标记查询（用户端/房东端共用）。支持 center+radius 或 bounds 模式。 */
    @Override
    public List<HouseMarkerVO> mapQuery(Double minLat, Double maxLat,
                                         Double minLng, Double maxLng,
                                         Double lat, Double lng, Double radius) {
        if (lat != null && lng != null && radius != null) {
            double[] box = GeoUtils.boundingBox(lat, lng, radius);
            minLat = box[0];
            maxLat = box[1];
            minLng = box[2];
            maxLng = box[3];
        }

        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            throw new BusinessException(MessageConstant.MAP_PARAM_INVALID);
        }

        if (minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180) {
            throw new BusinessException(MessageConstant.MAP_PARAM_INVALID);
        }

        return houseMapper.selectByBounds(minLat, maxLat, minLng, maxLng);
    }

    /** 房东端地图标记查询：返回当前房东名下所有房源标记。 */
    @Override
    public List<HouseMarkerVO> landlordMap() {
        Long landlordId = BaseContext.getCurrentId();
        return houseMapper.selectByLandlordMap(landlordId);
    }

    private void validateOwnership(Long houseId) {
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        if (!house.getLandlordId().equals(BaseContext.getCurrentId())) {
            throw new NoPermissionException(MessageConstant.HOUSE_NOT_OWNER);
        }
    }

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

        List<HouseImage> images = houseImageMapper.selectByHouseId(h.getId());
        if (images != null && !images.isEmpty()) {
            vo.setCoverImage(images.get(0).getUrl());
        }

        return vo;
    }

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

    private void saveTags(Long houseId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return;
        List<HouseTag> tags = tagNames.stream()
                .map(name -> HouseTag.builder().houseId(houseId).tagName(name).build())
                .collect(Collectors.toList());
        houseTagMapper.insertBatch(tags);
    }
}
