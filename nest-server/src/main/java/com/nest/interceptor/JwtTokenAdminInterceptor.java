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
 * 房东端 JWT 拦截器 —— 校验 Header token。
 */
@Slf4j
@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    /**
     * 请求进入 Controller 前的校验钩子（HandlerInterceptor 核心方法）。
     *
     * 职责：校验 Header 里的 `token` 是否有效，有效则把房东 ID 写入
     *
     * @param request  当前 HTTP 请求（从这里取 token 头）
     * @param response HTTP 响应（校验失败时设置 401 状态码）
     * @param handler  被调用的处理器对象（本方法不用）
     * @return true=放行进入 Controller；false=拦截（响应已返回 401）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 OPTIONS 预检请求（CORS 预检不带 token，由 CORS 配置处理）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 从请求头取出房东端 token（Header 名固定为 token）
        String token = request.getHeader(JwtConstant.ADMIN_TOKEN_NAME);
        if (token == null || token.isEmpty()) {
            // 没带 token 直接 401 拒绝，不进 Controller
            response.setStatus(401);
            log.warn("房东端请求缺少 token: {}", request.getRequestURI());
            return false;
        }

        try {
            // 用房东端密钥解析 JWT（密钥不符/过期都会抛异常）
            Claims claims = JwtUtil.parseToken(JwtConstant.ADMIN_SECRET_KEY, token);
            // 从载荷里取出 userId（签发时塞进去的）
            Long landlordId = claims.get("userId", Long.class);
            // 把房东 ID 和类型写入 ThreadLocal，Service 层用 BaseContext.getCurrentId() 读取
            BaseContext.setCurrentId(landlordId);
            BaseContext.setCurrentType(JwtConstant.TYPE_LANDLORD);
            log.debug("JWT 房东验证通过: landlordId={}", landlordId);
            return true;   // 校验通过，放行
        } catch (Exception e) {
            // token 无效/过期/被篡改 → 401
            response.setStatus(401);
            log.warn("房东端 token 无效: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 请求处理完成后的清理钩子。
     *
     * 必须在这里调用 {@link BaseContext#remove()} 清除 ThreadLocal。
     * 原因：Tomcat 线程池复用线程，若不清理，下一个请求会读到上一个请求残留的 userId（数据串号）。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.remove();   // 清空当前线程的 ThreadLocal，防止线程复用串号
    }
}
