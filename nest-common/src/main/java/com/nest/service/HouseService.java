package com.nest.service;

import com.nest.common.PageResult;
import com.nest.dto.HouseCreateDTO;
import com.nest.dto.HouseQueryDTO;
import com.nest.vo.HouseVO;
import org.springframework.web.multipart.MultipartFile;

/** 房源服务接口。 */
public interface HouseService {

    Long create(HouseCreateDTO dto);

    void update(Long houseId, HouseCreateDTO dto);

    void updateStatus(Long houseId, Integer status);

    void delete(Long houseId);

    PageResult<HouseVO> myList(Integer page, Integer pageSize);

    String uploadImage(MultipartFile file);

    PageResult<HouseVO> list(HouseQueryDTO dto);

    HouseVO detail(Long houseId);

    HouseVO getOwnedById(Long houseId);

    java.util.List<com.nest.vo.HouseMarkerVO> mapQuery(Double minLat, Double maxLat,
                                                         Double minLng, Double maxLng,
                                                         Double lat, Double lng, Double radius);

    java.util.List<com.nest.vo.HouseMarkerVO> landlordMap();
}
