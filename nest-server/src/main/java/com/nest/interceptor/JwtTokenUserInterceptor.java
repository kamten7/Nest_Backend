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

/**
 * 租客端 JWT 拦截器 —— 校验 Header authentication。
 */
@Slf4j
@Component
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    /**
     * 请求进入 Controller 前的校验钩子（租客端）。
     *
     * 职责：校验 Header `authentication` 里的租客 token，把租客 ID 写入
     * {@link BaseContext}。特殊地，评论列表等公开接口走"可选认证"——
     * 带 token 就解析身份（用于显示"是否已赞"），不带也放行（游客可浏览）。
     *
     * @param request  当前 HTTP 请求
     * @param response HTTP 响应（校验失败设 401）
     * @param handler  被调用的处理器对象（本方法不用）
     * @return true=放行；false=拦截（已返回 401）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 OPTIONS 预检请求（CORS 预检不带 token，由 CORS 配置处理）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 可选认证路径：评论列表等公开接口，带 token 就解析身份，不带也放行
        // 这样登录用户能看到"是否已赞"，未登录也能浏览
        if (isOptionalAuthPath(request.getRequestURI())) {
            String optionalToken = request.getHeader(JwtConstant.USER_TOKEN_NAME);
            if (optionalToken == null || optionalToken.isEmpty()) {
                return true;   // 没带 token → 当游客放行（BaseContext 为空，Service 层判空即可）
            }
            try {
                Claims claims = JwtUtil.parseToken(JwtConstant.USER_SECRET_KEY, optionalToken);
                BaseContext.setCurrentId(claims.get("userId", Long.class));
                BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
            } catch (Exception e) {
                // token 无效就当未登录，放行（不强制游客登录）
                log.warn("租客端可选认证 token 无效: {}", e.getMessage());
            }
            return true;   // 无论 token 是否有效，公开接口都放行
        }

        // 以下为普通受保护接口：必须带有效 token
        String token = request.getHeader(JwtConstant.USER_TOKEN_NAME);
        if (token == null || token.isEmpty()) {
            response.setStatus(401);   // 没带 token → 401 拒绝
            log.warn("租客端请求缺少 authentication: {}", request.getRequestURI());
            return false;
        }

        try {
            // 校验 token 有效性
            Claims claims = JwtUtil.parseToken(JwtConstant.USER_SECRET_KEY, token);
            // 从 token 中提取租客 ID
            // 这里假设 token 中包含 userId 字段，实际应用中根据业务逻辑调整
            Long tenantId = claims.get("userId", Long.class);
            // 写入 BaseContext，后续 Service 层直接获取
            BaseContext.setCurrentId(tenantId);
            // 写入用户类型：tenant
            // 这样后续 Service 层可以根据用户类型判断是否需要权限校验
            BaseContext.setCurrentType(JwtConstant.TYPE_TENANT);
            log.debug("JWT 租客验证通过: tenantId={}", tenantId);
            return true;
        } catch (Exception e) {
            response.setStatus(401);
            log.warn("租客端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    /** 可选认证路径：公开但若带 token 则解析用户身份 */
    private boolean isOptionalAuthPath(String uri) {
        // 评论列表是公开接口，但登录用户需要看到"是否已赞"状态
        return uri.startsWith("/user/review/house/");
    }

    /**
     * 请求处理完成后的清理钩子：清空 ThreadLocal，防止线程复用串号。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();
    }
}
