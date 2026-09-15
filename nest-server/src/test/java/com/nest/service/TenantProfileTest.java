package com.nest.service;

import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.dto.TenantProfileDTO;
import com.nest.entity.Tenant;
import com.nest.exception.BusinessException;
import com.nest.mapper.TenantMapper;
import com.nest.minio.service.MinioService;
import com.nest.service.impl.TenantServiceImpl;
import com.nest.vo.TenantProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 租客个人信息（查询/更新/头像）服务单元测试（字段可选，只更新传值字段）。 */
@ExtendWith(MockitoExtension.class)
class TenantProfileTest {

    private static final String PHONE = "13800000000";
    private static final String AVATAR_URL = "http://localhost:9012/nest-avatar/avatar/1/ab12cd34.png";

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private MinioService minioService;

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

        assertThatCode(() -> tenantService.updateProfile(dto(PHONE)))
                .doesNotThrowAnyException();

        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("手机号为空串时不校验手机号、也不写空串，其余字段照常更新")
    void updateProfile_skipsPhoneCheck_whenPhoneIsEmpty() {
        TenantProfileDTO dto = dto("");
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        assertThatCode(() -> tenantService.updateProfile(dto))
                .doesNotThrowAnyException();

        verify(tenantMapper, never()).selectByPhone(anyString());
        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("只传昵称时也能更新（手机号完全为 null）")
    void updateProfile_success_whenOnlyNicknameProvided() {
        TenantProfileDTO dto = new TenantProfileDTO();
        dto.setNickname("只改昵称");
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        assertThatCode(() -> tenantService.updateProfile(dto))
                .doesNotThrowAnyException();

        verify(tenantMapper, never()).selectByPhone(anyString());
        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("一个字段都没传时直接跳过，不执行 UPDATE")
    void updateProfile_noop_whenNothingProvided() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        assertThatCode(() -> tenantService.updateProfile(new TenantProfileDTO()))
                .doesNotThrowAnyException();

        verify(tenantMapper, never()).update(any(Tenant.class));
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
    @DisplayName("已绑定手机号时 phoneBound 为 true")
    void getProfile_phoneBoundTrue_whenPhonePresent() {
        when(tenantMapper.selectById(1L))
                .thenReturn(Tenant.builder().id(1L).nickname("小王").avatar(AVATAR_URL).phone(PHONE).gender(1).build());

        TenantProfileVO vo = tenantService.getProfile();

        assertThat(vo.getNickname()).isEqualTo("小王");
        assertThat(vo.getPhone()).isEqualTo(PHONE);
        assertThat(vo.getGender()).isEqualTo(1);
        assertThat(vo.getPhoneBound()).isTrue();
    }

    @Test
    @DisplayName("未绑定手机号时 phoneBound 为 false（租房前置条件不满足）")
    void getProfile_phoneBoundFalse_whenPhoneMissing() {
        when(tenantMapper.selectById(1L)).thenReturn(Tenant.builder().id(1L).nickname("微信用户").build());

        TenantProfileVO vo = tenantService.getProfile();

        assertThat(vo.getPhone()).isNull();
        assertThat(vo.getPhoneBound()).isFalse();
    }

    @Test
    @DisplayName("查询个人信息未登录时抛出 NOT_LOGIN")
    void getProfile_throwsNotLogin_whenCurrentIdNull() {
        BaseContext.remove();

        assertThatThrownBy(() -> tenantService.getProfile())
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.NOT_LOGIN);
    }

    @Test
    @DisplayName("上传头像：走头像专用 bucket、按租客分目录，并写回 avatar 字段")
    void uploadAvatar_updatesTenantAvatarAndReturnsUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        when(tenantMapper.selectById(1L)).thenReturn(Tenant.builder().id(1L).build());
        when(minioService.uploadAvatar(any(), eq("avatar/1"))).thenReturn(AVATAR_URL);

        String url = tenantService.uploadAvatar(file);

        assertThat(url).isEqualTo(AVATAR_URL);
        verify(tenantMapper, times(1)).update(any(Tenant.class));
    }

    @Test
    @DisplayName("上传空文件时抛出 IMAGE_UPLOAD_EMPTY，且不查租客、不落库")
    void uploadAvatar_throws_whenFileEmpty() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> tenantService.uploadAvatar(empty))
                .isInstanceOf(BusinessException.class)
                .hasMessage(MessageConstant.IMAGE_UPLOAD_EMPTY);

        verify(tenantMapper, never()).update(any(Tenant.class));
    }
}
