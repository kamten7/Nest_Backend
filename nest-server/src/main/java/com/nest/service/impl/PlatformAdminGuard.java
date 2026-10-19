package com.nest.service.impl;

import com.nest.common.BaseContext;
import com.nest.constant.MessageConstant;
import com.nest.exception.NoPermissionException;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminGuard {

    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public void require() {
        if (!ROLE_PLATFORM_ADMIN.equals(BaseContext.getCurrentRole())) {
            throw new NoPermissionException(MessageConstant.NO_PERMISSION + "：需要平台管理员");
        }
    }
}
