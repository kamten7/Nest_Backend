package com.nest.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.dto.LandlordLoginDTO;
import com.nest.entity.Landlord;
import com.nest.exception.BusinessException;
import com.nest.mapper.LandlordMapper;
import com.nest.service.LandlordService;
import com.nest.utils.JwtUtil;
import com.nest.utils.PasswordEncoderUtil;
import com.nest.vo.LandlordLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 房东服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandlordServiceImpl implements LandlordService {

    private final LandlordMapper landlordMapper;

    @Override
    public LandlordLoginVO login(LandlordLoginDTO dto) {
        // 1. 查数据库
        Landlord landlord = landlordMapper.selectByPhone(dto.getPhone());
        if (landlord == null) {
            throw new BusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 2. 验密码（优先 BCrypt，兼容历史 MD5）
        String rawPassword = dto.getPassword();
        String storedPassword = landlord.getPassword();
        if (!PasswordEncoderUtil.matches(rawPassword, storedPassword)) {
            // BCrypt 校验失败，且存的是 32 位十六进制（疑似 MD5）时，走 MD5 兼容校验
            boolean md5Matched = isMd5Hex(storedPassword)
                    && DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8)).equals(storedPassword);
            // MD5
            if (md5Matched) {
                // MD5 匹配成功，自动升级为 BCrypt 密文
                landlordMapper.updatePassword(landlord.getId(), PasswordEncoderUtil.encode(rawPassword));
                log.info("房东密码自动升级为 BCrypt: id={}", landlord.getId());
            } else {
                throw new BusinessException(MessageConstant.PASSWORD_ERROR);
            }
        }

        // 3. 查状态
        if (landlord.getStatus() == 0) {
            throw new BusinessException(MessageConstant.ACCOUNT_DISABLED);
        }

        // 4. 签发 JWT（claims 里放 userId + userType）
        String token = JwtUtil.createToken(
                JwtConstant.ADMIN_SECRET_KEY,
                JwtConstant.ADMIN_TTL,
                Map.of("userId", landlord.getId(), "userType", "landlord"));

        log.info("房东登录成功: id={}, phone={}", landlord.getId(), landlord.getPhone());
        return LandlordLoginVO.builder()
                .id(landlord.getId())
                .name(landlord.getName())
                .phone(landlord.getPhone())
                .token(token)
                .build();
    }

    /**
     * 判断字符串是否为 32 位十六进制（MD5 摘要形态）。
     */
    private static boolean isMd5Hex(String s) {
        return s != null && s.matches("[0-9a-fA-F]{32}");
    }
}
