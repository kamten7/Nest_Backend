package com.nest.constant;

/**
 * 预约状态机常量。
 *
 * 
 * 待确认(1) ──房东确认──▶ 已确认(2) ──看房完成──▶ 已看房(3) ──签约──▶ 已成交(5)
 *     │                     │
 *     │ 任一方取消            │ 任一方取消
 *     ▼                     ▼
 * 已取消(4)            已取消(4)
 * 
 */
public final class AppointmentStatus {

    private AppointmentStatus() {}

    /** 待确认 */
    public static final int PENDING = 1;
    /** 已确认 */
    public static final int CONFIRMED = 2;
    /** 已看房 */
    public static final int VISITED = 3;
    /** 已取消 */
    public static final int CANCELLED = 4;
    /** 已成交 */
    public static final int DEAL = 5;

    /** 状态文本 */
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
