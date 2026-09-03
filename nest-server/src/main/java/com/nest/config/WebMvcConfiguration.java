package com.nest.config;

import com.nest.interceptor.JwtTokenAdminInterceptor;
import com.nest.interceptor.JwtTokenUserInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 双通道拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final JwtTokenAdminInterceptor jwtTokenAdminInterceptor;
    private final JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /** 注册拦截器 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 房东端拦截器：拦截 /admin/**，排除登录
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/landlord/login");

        // 租客端拦截器：拦截 /user/**，排除登录和公开接口
        // 注意：/user/review/house/**（评论列表）不在排除列表，但在 JwtTokenUserInterceptor 里做"可选认证"（带 token 解析身份、不带放行）
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .excludePathPatterns(
                        "/user/tenant/login",
                        "/user/tenant/register",
                        "/user/house/list",
                        "/user/house/detail/**",
                        "/user/house/map"
                );
    }

    /** Knife4j 静态资源映射 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * 跨域配置 —— 允许前端开发服务器访问。
     * 开发环境放开所有来源；上线生产时应收紧为具体域名。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
