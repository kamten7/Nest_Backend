package com.nest.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有器。
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    // 创建应用上下文实例
    private static ApplicationContext context;

    // 设置应用上下文实例
    @Override
    public void setApplicationContext(
            ApplicationContext applicationContext// 应用上下文实例
    ) throws BeansException {
        context = applicationContext;
    }

    /** 按类型获取 Bean */
    public static <T> T getBean(
            Class<T> clazz// Bean 类
    ) {
        return context.getBean(clazz);// 获取 Bean 实例
    }
}
