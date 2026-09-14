package com.nest.service.impl;

import com.nest.common.BaseContext;
import com.nest.config.WeChatProperties;
import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.dto.TenantLoginDTO;
import com.nest.dto.TenantProfileDTO;
import com.nest.dto.TenantRegisterDTO;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.TenantMapper;
import com.nest.service.TenantService;
import com.nest.service.WxAuthService;
import com.nest.utils.JwtUtil;
import com.nest.vo.TenantLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 租客服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    private final TenantMapper tenantMapper;
    private final WxAuthService wxAuthService;
    private final WeChatProperties weChatProperties;

    /** 租客登录（支持微信 code 或手机号）。 */
    @Override
    public TenantLoginVO login(TenantLoginDTO dto) {
        Tenant tenant;

        if (dto.getCode() != null && !dto.getCode().isEmpty()) {
            String openid = resolveOpenid(dto.getCode());
            tenant = tenantMapper.selectByOpenid(openid);
            if (tenant == null) {
                tenant = Tenant.builder()
                        .openid(openid)
                        .nickname(dto.getNickname() != null ? dto.getNickname() : "微信用户")
                        .avatar(dto.getAvatar())
                        .status(1)
                        .build();
                try {
                    tenantMapper.insert(tenant);
                    log.info("微信新用户自动注册: id={}", tenant.getId());
                } catch (DuplicateKeyException e) {
                    // 并发首次登录：另一线程已插入相同 openid，回查继续登录
                    log.info("并发首次登录，回查已存在的租客");
                    tenant = tenantMapper.selectByOpenid(openid);
                    if (tenant == null) {
                        throw e;
                    }
                }
            }
        } else if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            tenant = tenantMapper.selectByPhone(dto.getPhone());
            if (tenant == null) {
                throw new BusinessException("该手机号尚未注册，请先注册");
            }
        } else {
            throw new BusinessException("请提供登录凭证（微信 code 或手机号）");
        }

        String token = JwtUtil.createToken(
                JwtConstant.userSecretKey(),
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

    /** 租客注册（手机号）。 */
    @Override
    public TenantLoginVO register(TenantRegisterDTO dto) {
        Tenant exist = tenantMapper.selectByPhone(dto.getPhone());
        if (exist != null) {
            throw new BusinessException("该手机号已注册");
        }

        Tenant tenant = Tenant.builder()
                .openid(null)
                .nickname(dto.getNickname())
                .avatar(dto.getAvatar())
                .phone(dto.getPhone())
                .gender(dto.getGender())
                .status(1)
                .build();
        tenantMapper.insert(tenant);

        String token = JwtUtil.createToken(
                JwtConstant.userSecretKey(),
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

    /** 更新租客信息。 */
    @Override
    public void updateProfile(TenantProfileDTO dto) {
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }

        Tenant tenant = tenantMapper.selectById(currentId);
        if (tenant == null) {
            throw new BusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        String phone = dto.getPhone();
        if (phone == null || phone.isEmpty()) {
            throw new BusinessException(MessageConstant.PHONE_INVALID);
        }

        Tenant exist = tenantMapper.selectByPhone(phone);
        if (exist != null && !exist.getId().equals(currentId)) {
            throw new BusinessException(MessageConstant.PHONE_ALREADY_REGISTERED);
        }

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

    /** 用微信 code 换取 openid。mock 模式返回伪造值（仅本地开发），否则调用微信 jscode2session。 */
    private String resolveOpenid(String code) {
        if (weChatProperties.isMockEnabled()) {
            log.warn("微信登录 MOCK 模式：openid 为伪造值，仅限本地开发。code={}", code);
            return "wx_" + code;
        }
        return wxAuthService.code2Openid(code);
    }
}
