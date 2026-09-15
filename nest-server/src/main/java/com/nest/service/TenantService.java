package com.nest.service;

import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantProfileDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.vo.TenantLoginVO;
import com.nest.vo.TenantProfileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租客服务接口。
 */
public interface TenantService {

    /** 租客登录（支持微信 code 和手机号两种方式） */
    TenantLoginVO login(TenantLoginDTO dto);

    /** 租客注册 */
    TenantLoginVO register(TenantRegisterDTO dto);

    /** 查询当前登录租客的个人信息 */
    TenantProfileVO getProfile();

    /** 更新当前登录租客的个人信息（昵称 / 手机号 / 头像 / 性别，字段均可选） */
    void updateProfile(TenantProfileDTO dto);

    /** 上传头像到头像专用 bucket 并直接更新当前租客头像，返回可访问的头像 URL */
    String uploadAvatar(MultipartFile file);
}
