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
import com.nest.minio.service.MinioService;
import com.nest.service.TenantService;
import com.nest.service.WxAuthService;
import com.nest.utils.JwtUtil;
import com.nest.vo.TenantLoginVO;
import com.nest.vo.TenantProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 租客服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    /** 头像在 bucket 内的目录前缀 */
    private static final String AVATAR_FOLDER = "avatar";

    private final TenantMapper tenantMapper;
    private final WxAuthService wxAuthService;
    private final WeChatProperties weChatProperties;
    private final MinioService minioService;

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
                .phone(tenant.getPhone())
                .gender(tenant.getGender())
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
                .phone(tenant.getPhone())
                .gender(tenant.getGender())
                .token(token)
                .build();
    }

    /** 查询当前登录租客的个人信息。 */
    @Override
    public TenantProfileVO getProfile() {
        return toProfileVO(requireCurrentTenant());
    }

    /**
     * 更新租客个人信息。所有字段均可选，只更新「传了值」的字段。
     *
     * <p>⚠️ 手机号<b>只校验格式长度与唯一性</b>，不校验号码是否真实存在/是否本人。
     * 短信验证码校验属于「上线并真正投入使用后」才启用的能力，与微信支付一样先预留：
     * 届时在 {@code phone != null} 分支前加一步「校验短信验证码」即可，接口契约不变。
     */
    @Override
    public void updateProfile(TenantProfileDTO dto) {
        Tenant tenant = requireCurrentTenant();
        Long currentId = tenant.getId();

        String phone = trimToNull(dto.getPhone());
        String nickname = trimToNull(dto.getNickname());
        String avatar = trimToNull(dto.getAvatar());

        if (phone == null && nickname == null && avatar == null && dto.getGender() == null) {
            log.info("租客个人信息更新：没有可更新字段，跳过, id={}", currentId);
            return;
        }

        if (phone != null) {
            // 唯一性校验：不允许两个账号绑同一手机号，否则手机号登录会歧义
            Tenant exist = tenantMapper.selectByPhone(phone);
            if (exist != null && !exist.getId().equals(currentId)) {
                throw new BusinessException(MessageConstant.PHONE_ALREADY_REGISTERED);
            }
        }

        Tenant updated = Tenant.builder()
                .id(currentId)
                .phone(phone)
                .nickname(nickname)
                .avatar(avatar)
                .gender(dto.getGender())
                .build();
        // update 是动态 SQL 且必然带上 update_time = NOW()，影响行数为 0 只可能是「值没变化」，
        // 不能据此判定失败（用户连续点保存就会命中），故这里不校验 rows。
        tenantMapper.update(updated);
        log.info("租客个人信息已更新: id={}, phone={}, nickname={}, gender={}", currentId, phone, nickname, dto.getGender());
    }

    /** 上传头像到「头像专用 bucket」并直接写回租客资料，返回头像 URL（前端选完图即可生效）。 */
    @Override
    public String uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(MessageConstant.IMAGE_UPLOAD_EMPTY);
        }
        Tenant tenant = requireCurrentTenant();

        // 按租客 ID 分目录，便于排查与清理
        String url = minioService.uploadAvatar(file, AVATAR_FOLDER + "/" + tenant.getId());
        tenantMapper.update(Tenant.builder().id(tenant.getId()).avatar(url).build());

        log.info("租客头像已更新: id={}, url={}", tenant.getId(), url);
        return url;
    }

    /** 取当前登录租客；未登录 / 账号不存在直接抛业务异常。 */
    private Tenant requireCurrentTenant() {
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BusinessException(MessageConstant.NOT_LOGIN);
        }
        Tenant tenant = tenantMapper.selectById(currentId);
        if (tenant == null) {
            throw new BusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        return tenant;
    }

    private TenantProfileVO toProfileVO(Tenant tenant) {
        String phone = tenant.getPhone();
        return TenantProfileVO.builder()
                .id(tenant.getId())
                .nickname(tenant.getNickname())
                .avatar(tenant.getAvatar())
                .phone(phone)
                .gender(tenant.getGender())
                .phoneBound(phone != null && !phone.isBlank())
                .build();
    }

    /** 空串 / 纯空白一律归一成 null，表示「该字段不修改」。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 用微信 code 换取 openid。mock 模式返回伪造值（仅本地开发），否则调用微信 jscode2session。 */
    private String resolveOpenid(String code) {
        if (weChatProperties.isMockEnabled()) {
            String mockOpenid = weChatProperties.getMockOpenid();
            if (mockOpenid != null && !mockOpenid.isBlank()) {
                // 固定 openid：uni.login 的 code 每次都是新的，按 code 派生会导致每次登录都变成新租客
                // （历史消息"消失"、会话对不上），本地开发统一映射到同一个账号。
                log.warn("微信登录 MOCK 模式：使用固定 openid={}，本次 code 已忽略", mockOpenid);
                return mockOpenid;
            }
            log.warn("微信登录 MOCK 模式：未配置固定 openid，按 code 派生（每次登录都会新建租客）。code={}", code);
            return "wx_" + code;
        }
        return wxAuthService.code2Openid(code);
    }
}
