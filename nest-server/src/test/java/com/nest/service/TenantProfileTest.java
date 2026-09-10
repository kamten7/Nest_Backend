package com.nest.service;

import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.dto.TenantProfileDTO;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.TenantMapper;
import com.nest.service.impl.TenantServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租客完善个人信息服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TenantProfileTest {

    private static final String PHONE = "13800000000";

    @Mock
    private TenantMapper tenantMapper;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    private TenantProfileDTO dto(String phone) {
        TenantProfileDTO dto = new TenantProfileDTO();
        dto.setPhone(phone);
        dto.setNickname("测试昵称");
        dto.setAvatar("https://example.com/avatar.png");
        return dto;
    }

    private Tenant tenant(long id, String phone) {
        return Tenant.builder().id(id).phone(phone).build();
    }

    @Test
    @DisplayName("手机号无冲突时正常更新个人信息")
    void updateProfile_success_whenPhoneNotTaken() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));
        when(tenantMapper.selectByPhone(PHONE)).thenReturn(null);
        when(tenantMapper.update(any(Tenant.class))).thenReturn(1);

        assertThatCode(() -> tenantService.updateProfile(dto(PHONE)))
                .doesNotThrowAnyException();

        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("手机号已被其他租客占用时抛出 PHONE_ALREADY_REGISTERED 且不执行更新")
    void updateProfile_throwsPhoneAlreadyRegistered_whenPhoneBelongsToAnotherTenant() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));
        when(tenantMapper.selectByPhone(PHONE)).thenReturn(tenant(2L, PHONE));

        assertThatThrownBy(() -> tenantService.updateProfile(dto(PHONE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.PHONE_ALREADY_REGISTERED);

        verify(tenantMapper, never()).update(any(Tenant.class));
    }

    @Test
    @DisplayName("本人重复提交自己的手机号时不误判冲突，正常更新")
    void updateProfile_success_whenReSubmittingOwnPhone() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, PHONE));
        when(tenantMapper.selectByPhone(PHONE)).thenReturn(tenant(1L, PHONE));
        when(tenantMapper.update(any(Tenant.class))).thenReturn(1);

        assertThatCode(() -> tenantService.updateProfile(dto(PHONE)))
                .doesNotThrowAnyException();

        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("未登录时抛出 NOT_LOGIN")
    void updateProfile_throwsNotLogin_whenCurrentIdNull() {
        BaseContext.remove();

        assertThatThrownBy(() -> tenantService.updateProfile(dto(PHONE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.NOT_LOGIN);
    }

    @Test
    @DisplayName("租客不存在时抛出 ACCOUNT_NOT_FOUND")
    void updateProfile_throwsAccountNotFound_whenTenantMissing() {
        when(tenantMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.updateProfile(dto(PHONE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("手机号为空时抛出 PHONE_INVALID")
    void updateProfile_throwsPhoneInvalid_whenPhoneEmpty() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        assertThatThrownBy(() -> tenantService.updateProfile(dto("")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.PHONE_INVALID);
    }
}
