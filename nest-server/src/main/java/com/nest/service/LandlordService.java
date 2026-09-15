package com.nest.service;

import com.nest.dto.LandlordLoginDTO;
import com.nest.vo.LandlordLoginVO;

/** 房东服务接口 */
public interface LandlordService {

    /** 房东登录，返回 JWT Token */
    LandlordLoginVO login(LandlordLoginDTO dto);
}
