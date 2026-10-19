package com.nest.common;

public class BaseContext {

    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

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

    public static void setCurrentRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getCurrentRole() {
        return CURRENT_ROLE.get();
    }

    public static void remove() {
        CURRENT_ID.remove();
        CURRENT_TYPE.remove();
        CURRENT_ROLE.remove();
    }
}
