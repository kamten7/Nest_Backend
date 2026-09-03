package com.nest.common;

/**
 * ThreadLocal 用户上下文，JWT 拦截器解析后注入。
 *
 * <p>用法：Service 层通过 {@code BaseContext.getCurrentId()} 获取当前请求的用户/房东 ID，
 * 通过 {@code BaseContext.getCurrentType()} 获取类型（tenant/landlord）。</p>
 */
public class BaseContext {

    // BaseContext 类加载时就创建了两个 ThreadLocal 对象，放在静态区
    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TYPE = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);
    }

    public static Long getCurrentId() {
        return CURRENT_ID.get();
    }

    public static void setCurrentType(String type) {
        CURRENT_TYPE.set(type);
    }

    public static String getCurrentType() {
        return CURRENT_TYPE.get();
    }

    public static void remove() {
        CURRENT_ID.remove();
        CURRENT_TYPE.remove();
    }
}
