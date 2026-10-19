package com.nest.interceptor;

import com.nest.common.BaseContext;
import com.nest.constant.JwtConstant;
import com.nest.service.impl.TokenBlacklistService;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (isOptionalAuthPath(request.getRequestURI())) {
            String optionalToken = request.getHeader(JwtConstant.USER_TOKEN_NAME);
            if (optionalToken == null || optionalToken.isEmpty()) {
                return true;
            }
            try {
                Claims claims = JwtUtil.parseToken(JwtConstant.userSecretKey(), optionalToken);
                if (tokenBlacklistService.isBlacklisted(claims.getId())) {
                    return true;
                }
                BaseContext.setCurrentId(claims.get("userId", Long.class));
                BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
                BaseContext.setCurrentRole("TENANT");
            } catch (Exception e) {
                log.warn("租客端可选认证 token 无效: {}", e.getMessage());
            }
            return true;
        }

        String token = request.getHeader(JwtConstant.USER_TOKEN_NAME);
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            log.warn("租客端请求缺少 authentication: {}", request.getRequestURI());
            return false;
        }

        try {
            Claims claims = JwtUtil.parseToken(JwtConstant.userSecretKey(), token);
            if (tokenBlacklistService.isBlacklisted(claims.getId())) {
                response.setStatus(401);
                log.warn("租客端 token 已吊销: {}", request.getRequestURI());
                return false;
            }
            Long tenantId = claims.get("userId", Long.class);
            BaseContext.setCurrentId(tenantId);
            BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
            BaseContext.setCurrentRole("TENANT");
            log.debug("JWT 租客验证通过: tenantId={}", tenantId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            log.warn("租客端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    private boolean isOptionalAuthPath(String uri) {
        return uri.startsWith("/user/review/house/") || uri.startsWith("/user/house/detail/");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
