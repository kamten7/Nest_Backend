package com.nest.constant;

/** 预约状态机常量。 */
public final class AppointmentStatus {

    private AppointmentStatus() {}

    public static final int PENDING = 1;
    public static final int CONFIRMED = 2;
    public static final int VISITED = 3;
    public static final int CANCELLED = 4;
    public static final int DEAL = 5;

    public static String text(int status) {
        return switch (status) {
            case PENDING -> "待确认";
            case CONFIRMED -> "已确认";
            case VISITED -> "已看房";
            case CANCELLED -> "已取消";
            case DEAL -> "已成交";
            default -> "未知";
        };
    }
}
