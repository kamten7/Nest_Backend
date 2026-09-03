package com.nest.service;

import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.vo.TenantLoginVO;

/**
 * 租客服务接口。
 */
public interface TenantService {

    /** 租客登录（支持微信 code 和手机号两种方式） */
    TenantLoginVO login(TenantLoginDTO dto);

    /** 租客注册 */
    TenantLoginVO register(TenantRegisterDTO dto);
}
