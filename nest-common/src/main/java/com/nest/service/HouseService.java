package com.nest.service;

import com.nest.common.PageResult;
import com.nest.dto.HouseCreateDTO;
import com.nest.dto.HouseQueryDTO;
import com.nest.vo.HouseVO;
import org.springframework.web.multipart.MultipartFile;

/** 房源服务接口。 */
public interface HouseService {

    /** 发布房源，返回房源 ID */
    Long create(HouseCreateDTO dto);

    /** 编辑房源 */
    void update(Long houseId, HouseCreateDTO dto);

    /** 上架/下架 */
    void updateStatus(Long houseId, Integer status);

    /** 删除房源（含图片和标签） */
    void delete(Long houseId);

    /** 房东查看自己的房源列表 */
    PageResult<HouseVO> myList(Integer page, Integer pageSize);

    /** 上传图片到 MinIO，返回 URL */
    String uploadImage(MultipartFile file);

    /** 用户端房源列表（公开，分页+筛选） */
    PageResult<HouseVO> list(HouseQueryDTO dto);

    /** 用户端房源详情（含图片+标签+房东信息） */
    HouseVO detail(Long houseId);

    /** 房东查看自己的房源详情（含图片+标签，即使下架，编辑回填用） */
    HouseVO getOwnedById(Long houseId);

    /** 用户端地图标记查询：支持 bounds 模式或 center+radius 模式 */
    java.util.List<com.nest.vo.HouseMarkerVO> mapQuery(Double minLat, Double maxLat,
                                                         Double minLng, Double maxLng,
                                                         Double lat, Double lng, Double radius);

    /** 房东端地图标记查询：返回当前房东的所有房源标记 */
    java.util.List<com.nest.vo.HouseMarkerVO> landlordMap();
}
