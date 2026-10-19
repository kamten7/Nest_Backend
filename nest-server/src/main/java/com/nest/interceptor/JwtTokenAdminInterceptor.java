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
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(JwtConstant.ADMIN_TOKEN_NAME);
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            log.warn("房东端请求缺少 token: {}", request.getRequestURI());
            return false;
        }

        try {
            Claims claims = JwtUtil.parseToken(JwtConstant.adminSecretKey(), token);
            if (tokenBlacklistService.isBlacklisted(claims.getId())) {
                response.setStatus(401);
                log.warn("房东端 token 已吊销: {}", request.getRequestURI());
                return false;
            }
            Long landlordId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            BaseContext.setCurrentId(landlordId);
            BaseContext.setCurrentType(JwtConstant.TYPE_LANDLORD);
            BaseContext.setCurrentRole(role == null || role.isBlank() ? "LANDLORD" : role);
            log.debug("JWT 房东验证通过: landlordId={}", landlordId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            log.warn("房东端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
