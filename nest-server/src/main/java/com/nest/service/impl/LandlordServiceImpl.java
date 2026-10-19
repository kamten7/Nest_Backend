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

@Slf4j
@Service
@RequiredArgsConstructor
public class LandlordServiceImpl implements LandlordService {

    private final LandlordMapper landlordMapper;
    private final LoginAttemptService loginAttemptService;

    @Override
    public LandlordLoginVO login(LandlordLoginDTO dto) {
        String ip = LoginAttemptService.currentIp();
        loginAttemptService.checkAllowed("landlord", dto.getPhone(), ip);

        Landlord landlord = landlordMapper.selectByPhone(dto.getPhone());
        if (landlord == null) {
            loginAttemptService.onFailure("landlord", dto.getPhone(), ip);

            throw new BusinessException(MessageConstant.PASSWORD_ERROR);
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
                loginAttemptService.onFailure("landlord", dto.getPhone(), ip);
                throw new BusinessException(MessageConstant.PASSWORD_ERROR);
            }
        }

        if (landlord.getStatus() == 0) {
            throw new BusinessException(MessageConstant.ACCOUNT_DISABLED);
        }

        String role = landlord.getRole() == null || landlord.getRole().isBlank()
                ? "LANDLORD" : landlord.getRole();
        String token = JwtUtil.createToken(
                JwtConstant.adminSecretKey(),
                JwtConstant.ADMIN_TTL,
                Map.of("userId", landlord.getId(), "userType", "landlord", "role", role));

        loginAttemptService.onSuccess("landlord", dto.getPhone());
        log.info("房东登录成功: id={}, phone={}", landlord.getId(), landlord.getPhone());
        return LandlordLoginVO.builder()
                .id(landlord.getId())
                .name(landlord.getName())
                .phone(landlord.getPhone())
                .token(token)
                .build();
    }

    private static boolean isMd5Hex(String s) {
        return s != null && s.matches("[0-9a-fA-F]{32}");
    }
}
