package com.nest.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.nest.common.BaseContext;
import com.nest.common.PageParam;
import com.nest.common.PageResult;
import com.nest.constant.MessageConstant;
import com.nest.entity.Favorite;
import com.nest.entity.House;
import com.nest.entity.HouseImage;
import com.nest.exception.BusinessException;
import com.nest.mapper.FavoriteMapper;
import com.nest.mapper.HouseImageMapper;
import com.nest.mapper.HouseMapper;
import com.nest.service.FavoriteService;
import com.nest.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 收藏服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final HouseMapper houseMapper;
    private final HouseImageMapper houseImageMapper;

    /** 添加收藏。 */
    @Override
    public void add(Long houseId) {
        Long tenantId = BaseContext.getCurrentId();

        House house = houseMapper.selectById(houseId);
        if (house == null || house.getStatus() == 0) {
            throw new BusinessException(MessageConstant.HOUSE_NOT_FOUND);
        }

        Favorite favorite = Favorite.builder()
                .tenantId(tenantId)
                .houseId(houseId)
                .build();
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            log.warn("重复收藏被拦截: tenantId={}, houseId={}", tenantId, houseId);
            throw new BusinessException(MessageConstant.FAVORITE_DUPLICATE);
        }
        log.info("添加收藏: tenantId={}, houseId={}", tenantId, houseId);
    }

    /** 取消收藏。 */
    @Override
    public void remove(Long houseId) {
        Long tenantId = BaseContext.getCurrentId();
        int rows = favoriteMapper.deleteByTenantAndHouse(tenantId, houseId);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.FAVORITE_NOT_FOUND);
        }
        log.info("取消收藏: tenantId={}, houseId={}", tenantId, houseId);
    }

    /** 我的收藏列表（分页）。 */
    @Override
    public PageResult<FavoriteVO> myList(Integer page, Integer pageSize) {
        Long tenantId = BaseContext.getCurrentId();
        PageHelper.startPage(PageParam.pageOf(page), PageParam.pageSizeOf(pageSize));
        List<Favorite> favorites = favoriteMapper.selectByTenant(tenantId);
        PageInfo<Favorite> pageInfo = new PageInfo<>(favorites);

        if (favorites.isEmpty()) {
            return PageResult.of(0L, Collections.emptyList());
        }

        List<FavoriteVO> vos = new ArrayList<>();
        for (Favorite f : favorites) {
            House house = houseMapper.selectById(f.getHouseId());
            if (house == null) continue;

            FavoriteVO vo = new FavoriteVO();
            vo.setFavoriteId(f.getId());
            vo.setHouseId(house.getId());
            vo.setHouseTitle(house.getTitle());
            vo.setPrice(house.getPrice());
            vo.setArea(house.getArea());
            vo.setDistrict(house.getDistrict());
            vo.setAddress(house.getAddress());
            vo.setCreateTime(f.getCreateTime());

            List<HouseImage> images = houseImageMapper.selectByHouseId(house.getId());
            if (images != null && !images.isEmpty()) {
                vo.setCoverImage(images.get(0).getUrl());
            }
            vos.add(vo);
        }
        return PageResult.of(pageInfo.getTotal(), vos);
    }

    /** 判断是否已收藏。 */
    @Override
    public boolean hasFavorited(Long houseId) {
        Long tenantId = BaseContext.getCurrentId();
        return favoriteMapper.selectByTenantAndHouse(tenantId, houseId) != null;
    }
}
