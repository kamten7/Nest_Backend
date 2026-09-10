package com.nest.service.impl;

import com.nest.common.BaseContext;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantProfileDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.TenantMapper;
import com.nest.service.TenantService;
import com.nest.utils.JwtUtil;
import com.nest.vo.TenantLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 租客服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantMapper tenantMapper;

    @Override
    public TenantLoginVO login(TenantLoginDTO dto) {
        Tenant tenant;

        if (dto.getCode() != null && !dto.getCode().isEmpty()) {
            // 微信 code 登录：用 code 换 openid → 查库
            String openid = resolveOpenid(dto.getCode());
            tenant = tenantMapper.selectByOpenid(openid);
            if (tenant == null) {
                // 首次微信登录，自动注册
                tenant = Tenant.builder()
                        .openid(openid)
                        .nickname(dto.getNickname() != null ? dto.getNickname() : "微信用户")
                        .avatar(dto.getAvatar())
                        .status(1)
                        .build();
                tenantMapper.insert(tenant);
                log.info("微信新用户自动注册: openid={}, id={}", openid, tenant.getId());
            }
        } else if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            // 手机号登录
            tenant = tenantMapper.selectByPhone(dto.getPhone());
            if (tenant == null) {
                throw new BusinessException("该手机号尚未注册，请先注册");
            }
        } else {
            throw new BusinessException("请提供登录凭证（微信 code 或手机号）");
        }

        // 签发 JWT
        String token = JwtUtil.createToken(
                JwtConstant.USER_SECRET_KEY,
                JwtConstant.USER_TTL,
                Map.of("userId", tenant.getId(), "userType", "tenant"));

        log.info("租客登录成功: id={}, nickname={}", tenant.getId(), tenant.getNickname());
        return TenantLoginVO.builder()
                .id(tenant.getId())
                .nickname(tenant.getNickname())
                .avatar(tenant.getAvatar())
                .token(token)
                .build();
    }

    @Override
    public TenantLoginVO register(TenantRegisterDTO dto) {
        // 检查手机号是否已注册
        Tenant exist = tenantMapper.selectByPhone(dto.getPhone());
        if (exist != null) {
            throw new BusinessException("该手机号已注册");
        }

        // 创建租客
        Tenant tenant = Tenant.builder()
                .openid(UUID.randomUUID().toString()) // 手机注册的临时 openid
                .nickname(dto.getNickname())
                .avatar(dto.getAvatar())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .status(1)
                .build();
        tenantMapper.insert(tenant);

        // 签发 JWT
        String token = JwtUtil.createToken(
                JwtConstant.USER_SECRET_KEY,
                JwtConstant.USER_TTL,
                Map.of("userId", tenant.getId(), "userType", "tenant"));

        log.info("租客注册成功: id={}, phone={}", tenant.getId(), tenant.getPhone());
        return TenantLoginVO.builder()
                .id(tenant.getId())
                .nickname(tenant.getNickname())
                .avatar(tenant.getAvatar())
                .token(token)
                .build();
    }

    /**
     * 更新租客信息
     */
    @Override
    public void updateProfile(TenantProfileDTO dto) {
        // 获取当前登录租客 ID
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }

        // 校验租客是否存在
        Tenant tenant = tenantMapper.selectById(currentId);
        if (tenant == null) {
            throw new BusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 手机号不能为空
        String phone = dto.getPhone();
        if (phone == null || phone.isEmpty()) {
            throw new BusinessException(MessageConstant.PHONE_INVALID);
        }

        // 手机号唯一性校验（排除本人）
        Tenant exist = tenantMapper.selectByPhone(phone);
        if (exist != null && !exist.getId().equals(currentId)) {
            throw new BusinessException(MessageConstant.PHONE_ALREADY_REGISTERED);
        }

        // 构建更新实体（仅更新非空字段，空字段由 Mapper XML 跳过）
        Tenant updated = Tenant.builder()
                .id(currentId)
                .phone(dto.getPhone())
                .nickname(dto.getNickname())
                .avatar(dto.getAvatar())
                .build();
        int rows = tenantMapper.update(updated);
        if (rows == 0) {
            throw new BusinessException(MessageConstant.PROFILE_UPDATE_FAILED);
        }
    }

    /**
     * 用微信 code 换取 openid。
     * 需要微信小程序的 appId 和 secret，当前返回 mock 值用于开发调试。
     */
    private String resolveOpenid(String code) {
        // TODO: 接入微信 API 后替换为真实请求
        // GET https://api.weixin.qq.com/sns/jscode2session?appid=xxx&secret=xxx&js_code={code}&grant_type=authorization_code
        log.info("微信登录 code={}，返回 mock openid", code);
        return "wx_" + code;
    }
}
