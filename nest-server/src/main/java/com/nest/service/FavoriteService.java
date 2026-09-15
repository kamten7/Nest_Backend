package com.nest.service;

import com.nest.common.PageResult;
import com.nest.vo.FavoriteVO;

/** 收藏服务接口 */
public interface FavoriteService {

    /** 添加收藏（租客） */
    void add(Long houseId);

    /** 取消收藏（租客） */
    void remove(Long houseId);

    /** 我的收藏列表（分页） */
    PageResult<FavoriteVO> myList(Integer page, Integer pageSize);

    /** 是否已收藏 */
    boolean hasFavorited(Long houseId);
}
