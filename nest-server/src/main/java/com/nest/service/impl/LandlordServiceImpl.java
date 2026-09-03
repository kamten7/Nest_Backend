package com.nest.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.dto.LandlordLoginDTO;
import com.nest.entity.Landlord;
import com.nest.exception.BusinessException;
import com.nest.mapper.LandlordMapper;
import com.nest.service.LandlordService;
import com.nest.utils.JwtUtil;
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

        // 2. 验密码（MD5）
        String md5 = DigestUtils.md5DigestAsHex(dto.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!md5.equals(landlord.getPassword())) {
            throw new BusinessException(MessageConstant.PASSWORD_ERROR);
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
}
