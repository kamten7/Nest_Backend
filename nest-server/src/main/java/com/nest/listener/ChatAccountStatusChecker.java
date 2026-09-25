package com.nest.listener;

import com.nest.chat.core.ChatAccountChecker;
import com.nest.constant.JwtConstant;
import com.nest.entity.Landlord;
import com.nest.entity.Tenant;
import com.nest.mapper.LandlordMapper;
import com.nest.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 握手期账号可用性校验：status=0（封禁/注销）的账号即使持有效 token 也拒绝连接。 */
@Component
@RequiredArgsConstructor
public class ChatAccountStatusChecker implements ChatAccountChecker {

    private final TenantMapper tenantMapper;
    private final LandlordMapper landlordMapper;

    @Override
    public boolean isActive(String userType, Long userId) {
        if (userId == null) {
            return false;
        }
        if (JwtConstant.TYPE_LANDLORD.equals(userType)) {
            Landlord landlord = landlordMapper.selectById(userId);
            return landlord != null && landlord.getStatus() != null && landlord.getStatus() != 0;
        }
        if (JwtConstant.TYPE_TENANT.equals(userType)) {
            Tenant tenant = tenantMapper.selectById(userId);
            return tenant != null && tenant.getStatus() != null && tenant.getStatus() != 0;
        }
        return false;
    }
}
