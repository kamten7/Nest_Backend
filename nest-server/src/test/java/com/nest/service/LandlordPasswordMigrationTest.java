package com.nest.service;

import com.nest.constant.JwtConstant;
import com.nest.constant.MessageConstant;
import com.nest.dto.LandlordLoginDTO;
import com.nest.entity.Landlord;
import com.nest.exception.BusinessException;
import com.nest.mapper.LandlordMapper;
import com.nest.service.impl.LandlordServiceImpl;
import com.nest.utils.PasswordEncoderUtil;
import com.nest.vo.LandlordLoginVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 房东密码 BCrypt 升级与迁移单元测试（无 Spring 容器，需手动灌入 JWT 测试密钥）。 */
@ExtendWith(MockitoExtension.class)
class LandlordPasswordMigrationTest {

    private static final String PHONE = "13800138000";
    private static final String RAW_PASSWORD = "password123";

    @BeforeAll
    static void initJwtSecrets() {
        JwtConstant.initSecrets("unit-test-admin-secret", "unit-test-user-secret");
    }

    @Mock
    private LandlordMapper landlordMapper;

    @InjectMocks
    private LandlordServiceImpl landlordService;

    private Landlord landlord(String storedPassword, Integer status) {
        return Landlord.builder()
                .id(1L)
                .name("张三")
                .phone(PHONE)
                .password(storedPassword)
                .status(status)
                .build();
    }

    private LandlordLoginDTO dto(String phone, String rawPassword) {
        LandlordLoginDTO dto = new LandlordLoginDTO();
        dto.setPhone(phone);
        dto.setPassword(rawPassword);
        return dto;
    }

    @Test
    void bcryptStoredPassword_correctRaw_shouldLoginAndNotUpgrade() {
        String bcrypt = PasswordEncoderUtil.encode(RAW_PASSWORD);
        when(landlordMapper.selectByPhone(PHONE)).thenReturn(landlord(bcrypt, 1));

        LandlordLoginVO vo = landlordService.login(dto(PHONE, RAW_PASSWORD));

        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getPhone()).isEqualTo(PHONE);
        assertThat(vo.getToken()).isNotBlank();
        verify(landlordMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void legacyMd5StoredPassword_correctRaw_shouldLoginAndUpgrade() {
        String md5 = DigestUtils.md5DigestAsHex(RAW_PASSWORD.getBytes(StandardCharsets.UTF_8));
        when(landlordMapper.selectByPhone(PHONE)).thenReturn(landlord(md5, 1));

        LandlordLoginVO vo = landlordService.login(dto(PHONE, RAW_PASSWORD));

        assertThat(vo).isNotNull();
        assertThat(vo.getId()).isEqualTo(1L);
        verify(landlordMapper, times(1))
                .updatePassword(eq(1L), argThat(p -> p != null && p.startsWith("$2a$")));
    }

    @Test
    void wrongPassword_againstBcryptStore_shouldThrowPasswordErrorAndNotWrite() {
        String bcrypt = PasswordEncoderUtil.encode(RAW_PASSWORD);
        when(landlordMapper.selectByPhone(PHONE)).thenReturn(landlord(bcrypt, 1));

        assertThatThrownBy(() -> landlordService.login(dto(PHONE, "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.PASSWORD_ERROR);
        verify(landlordMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void unknownPhone_shouldThrowAccountNotFound() {
        when(landlordMapper.selectByPhone(PHONE)).thenReturn(null);

        assertThatThrownBy(() -> landlordService.login(dto(PHONE, RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    @Test
    void disabledAccount_shouldThrowAccountDisabled() {
        String bcrypt = PasswordEncoderUtil.encode(RAW_PASSWORD);
        when(landlordMapper.selectByPhone(PHONE)).thenReturn(landlord(bcrypt, 0));

        assertThatThrownBy(() -> landlordService.login(dto(PHONE, RAW_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.ACCOUNT_DISABLED);
    }
}
