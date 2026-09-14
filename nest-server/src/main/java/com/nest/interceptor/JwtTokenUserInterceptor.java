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

/** 租客端 JWT 拦截器。校验 Header authentication。 */
@Slf4j
@Component
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    /** 请求进入 Controller 前校验租客 token，写入 BaseContext。支持可选认证路径。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 可选认证路径：带 token 就解析身份，不带也放行（游客可浏览）
        if (isOptionalAuthPath(request.getRequestURI())) {
            String optionalToken = request.getHeader(JwtConstant.USER_TOKEN_NAME);
            if (optionalToken == null || optionalToken.isEmpty()) {
                return true;
            }
            try {
                Claims claims = JwtUtil.parseToken(JwtConstant.userSecretKey(), optionalToken);
                BaseContext.setCurrentId(claims.get("userId", Long.class));
                BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
            } catch (Exception e) {
                log.warn("租客端可选认证 token 无效: {}", e.getMessage());
            }
            return true;
        }

        // 普通受保护接口：必须带有效 token
        String token = request.getHeader(JwtConstant.USER_TOKEN_NAME);
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            log.warn("租客端请求缺少 authentication: {}", request.getRequestURI());
            return false;
        }

        try {
            Claims claims = JwtUtil.parseToken(JwtConstant.userSecretKey(), token);
            Long tenantId = claims.get("userId", Long.class);
            BaseContext.setCurrentId(tenantId);
            BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
            log.debug("JWT 租客验证通过: tenantId={}", tenantId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            log.warn("租客端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    /** 可选认证路径：公开但若带 token 则解析用户身份。 */
    private boolean isOptionalAuthPath(String uri) {
        return uri.startsWith("/user/review/house/");
    }

    /** 请求完成清理 ThreadLocal，防止线程复用串号。 */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
