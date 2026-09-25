package com.nest.listener;

import com.nest.entity.Landlord;
import com.nest.entity.Tenant;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P1-7：握手期账号状态校验——封禁(status=0)/不存在/未知身份即使持有效 token 也拒绝连接。 */
class ChatAccountStatusCheckerTest {

    private TenantMapper tenantMapper;
    private LandlordMapper landlordMapper;
    private ChatAccountStatusChecker checker;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        landlordMapper = mock(LandlordMapper.class);
        checker = new ChatAccountStatusChecker(tenantMapper, landlordMapper);
    }

    @Test
    void activeTenantAllowed() {
        when(tenantMapper.selectById(1L)).thenReturn(Tenant.builder().id(1L).status(1).build());
        assertThat(checker.isActive("tenant", 1L)).isTrue();
    }

    @Test
    void bannedTenantRejected() {
        when(tenantMapper.selectById(1L)).thenReturn(Tenant.builder().id(1L).status(0).build());
        assertThat(checker.isActive("tenant", 1L)).isFalse();
    }

    @Test
    void missingTenantRejected() {
        when(tenantMapper.selectById(1L)).thenReturn(null);
        assertThat(checker.isActive("tenant", 1L)).isFalse();
    }

    @Test
    void nullStatusRejected() {
        when(tenantMapper.selectById(1L)).thenReturn(Tenant.builder().id(1L).build());
        assertThat(checker.isActive("tenant", 1L)).isFalse();
    }

    @Test
    void bannedLandlordRejected() {
        when(landlordMapper.selectById(2L)).thenReturn(Landlord.builder().id(2L).status(0).build());
        assertThat(checker.isActive("landlord", 2L)).isFalse();
    }

    @Test
    void activeLandlordAllowed() {
        when(landlordMapper.selectById(2L)).thenReturn(Landlord.builder().id(2L).status(1).build());
        assertThat(checker.isActive("landlord", 2L)).isTrue();
    }

    @Test
    void unknownUserTypeRejected() {
        assertThat(checker.isActive("admin", 3L)).isFalse();
    }

    @Test
    void nullUserIdRejected() {
        assertThat(checker.isActive("tenant", null)).isFalse();
    }
}
