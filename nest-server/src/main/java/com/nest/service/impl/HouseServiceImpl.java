package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageParam;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


/** 房源服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseServiceImpl implements HouseService {

    private final static String CACHE_KEY_PREFIX="cache:house:detail:";
    private final static Integer CACHE_TTL_BASE = 30;
    private final static Integer CACHE_NULL_TTL = 2;
    private final static String CACHE_LOCK_PREFIX = "cache:house:lock:";
    private final static Integer CACHE_TTL_RANDOM = 10;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

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

    /**
     * 更新房源（房东端）。图片和标签采用"先删后插"整体替换策略。
     * 更新完成后，刷新缓存。
     */
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

        // 刷新缓存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictDetailCache(houseId);
                }
            });
        } else {
            evictDetailCache(houseId);
        }


    }

    /**
     * 上下架/重新发布（房东端）。status: 1=上架, 0=下架；在租中(2) 不允许手动改。
     */
    @Override
    public void updateStatus(Long houseId, Integer status) {
        if (status == null || (status != HouseStatus.AVAILABLE && status != HouseStatus.OFFLINE)) {
            log.warn("拒绝非法的房源状态参数: id={}, status={}", houseId, status);
            throw new BusinessException(MessageConstant.HOUSE_STATUS_INVALID);
        }
        validateOwnership(houseId);
        int rows = houseMapper.updateStatus(houseId, status);
        if (rows == 0) {
            // 能走到这里说明归属校验已通过（房源存在），行数为 0 只剩一种解释：刚被置入在租中
            throw new BusinessException(MessageConstant.HOUSE_RENTED_NO_MANUAL);
        }
        log.info("房源状态变更: id={}, status={}", houseId, status);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictDetailCache(houseId);
                }
            });
        } else {
            evictDetailCache(houseId);
        }

    }

    /**
     * 删除房源（房东端）。先删子表再删主表，同一事务内。
     */
    @Override
    @Transactional
    public void delete(Long houseId) {
        validateOwnership(houseId);

        House cur = houseMapper.selectByIdForUpdate(houseId);
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictDetailCache(houseId);
                }
            });
        } else {
            evictDetailCache(houseId);
        }
    }

    /** 我的房源列表（房东端，分页）。参数来自 @RequestParam 裸值，统一走 PageParam 收口。 */
    @Override
    public PageResult<HouseVO> myList(Integer page, Integer pageSize) {
        Long landlordId = BaseContext.getCurrentId();
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
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
     */
    @Override
    public HouseVO detail(Long houseId) {
        if (houseId == null || houseId <= 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        String key = CACHE_KEY_PREFIX + houseId;

        HouseVO cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        //先尝试去获取锁去重建缓存，获取锁成功→重建缓存→释放锁
        if (acquireRebuildLock(houseId)) {
            try {
                //尝试获取锁后，再次检查缓存，防止其他线程已经重建了缓存
                cached = readCache(key);
                if (cached != null) {
                    return cached;
                }

                House house = houseMapper.selectById(houseId);
                if (house == null) {
                    //如果不存在，就设置一个空字符串缓存，过期时间为10分钟，防止缓存穿透问题
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
                }

                HouseVO vo = loadDetailFromDb(house, houseId);
                if (house.getStatus() == 1) {
                    //如果存在，就写入缓存，过期时间为10分钟
                    writeCache(houseId, vo);
                }
                return vo;
            } finally {
                //释放锁
                stringRedisTemplate.delete(CACHE_LOCK_PREFIX + houseId);
            }
        }

        //前面没有拿到锁，就说明有其他线程正在重建缓存，这里就直接重试3次
        for (int i = 0; i < 3; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            //重新获取
            HouseVO retryCached = readCache(key);
            if (retryCached != null) {
                return retryCached;
            }
        }
        //三次重试后仍然没有缓存，就直接查库返回，兜底策略
        House house = houseMapper.selectById(houseId);
        if (house == null) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }
        return loadDetailFromDb(house, houseId);
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

    /**
     * 从数据库加载房源详情
     */
    private HouseVO loadDetailFromDb(House house, Long houseId) {
        Long currentId = BaseContext.getCurrentId();
        boolean rentedByMe = currentId != null
                && JwtConstant.TYPE_TENANT.equals(BaseContext.getCurrentType())
                && rentOrderMapper.countByTenantAndHouse(currentId, houseId) > 0;

        if (house.getStatus() != 1 && !rentedByMe) {
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

    /**
     * 读取房源缓存
     */
    private HouseVO readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            if (json.isBlank()) {
                throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
            }
            return objectMapper.readValue(json, HouseVO.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("房源缓存读取失败，降级查库: key={}", key, e);
            return null;
        }
    }

    /**
     * 写入房源缓存
     */
    private void writeCache(Long houseId, HouseVO vo) {
        try {
            long ttl = CACHE_TTL_BASE * 60 + ThreadLocalRandom.current().nextInt(CACHE_TTL_RANDOM * 60);
            stringRedisTemplate.opsForValue().set(CACHE_KEY_PREFIX + houseId,
                    objectMapper.writeValueAsString(vo), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("房源缓存写入失败: houseId={}", houseId, e);
        }
    }

    /**
     * 获取缓存重建锁
     */
    private boolean acquireRebuildLock(Long houseId) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(CACHE_LOCK_PREFIX + houseId, "1", Duration.ofSeconds(10));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放缓存重建锁
     */
    @Override
    public void evictCache(Long houseId) {
        evictDetailCache(houseId);
    }

    private void evictDetailCache(Long houseId) {
        stringRedisTemplate.delete(CACHE_KEY_PREFIX + houseId);
    }


}
