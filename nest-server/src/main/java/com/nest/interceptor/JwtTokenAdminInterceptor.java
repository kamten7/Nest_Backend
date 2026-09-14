package com.nest.interceptor;

import com.nest.common.BaseContext;
import com.nest.constant.JwtConstant;
import com.nest.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 房东端 JWT 拦截器。校验 Header token。 */
@Slf4j
@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    /** 请求进入 Controller 前校验房东 token，写入 BaseContext。 */
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
            Long landlordId = claims.get("userId", Long.class);
            BaseContext.setCurrentId(landlordId);
            BaseContext.setCurrentType(JwtConstant.TYPE_LANDLORD);
            log.debug("JWT 房东验证通过: landlordId={}", landlordId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            log.warn("房东端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    /** 请求完成清理 ThreadLocal，防止线程复用串号。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
