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

/** 房东服务实现 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandlordServiceImpl implements LandlordService {

    private final LandlordMapper landlordMapper;

    @Override
    public LandlordLoginVO login(LandlordLoginDTO dto) {
        Landlord landlord = landlordMapper.selectByPhone(dto.getPhone());
        if (landlord == null) {
            throw new BusinessException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        String rawPassword = dto.getPassword();
        String storedPassword = landlord.getPassword();
        if (!PasswordEncoderUtil.matches(rawPassword, storedPassword)) {
            boolean md5Matched = isMd5Hex(storedPassword)
                    && DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8)).equals(storedPassword);
            if (md5Matched) {
                landlordMapper.updatePassword(landlord.getId(), PasswordEncoderUtil.encode(rawPassword));
                log.info("房东密码自动升级为 BCrypt: id={}", landlord.getId());
            } else {
                throw new BusinessException(MessageConstant.PASSWORD_ERROR);
            }
        }

        if (landlord.getStatus() == 0) {
            throw new BusinessException(MessageConstant.ACCOUNT_DISABLED);
        }

        String token = JwtUtil.createToken(
                JwtConstant.adminSecretKey(),
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

    /** 判断字符串是否 32 位十六进制（MD5 摘要形态） */
    private static boolean isMd5Hex(String s) {
        return s != null && s.matches("[0-9a-fA-F]{32}");
    }
}
