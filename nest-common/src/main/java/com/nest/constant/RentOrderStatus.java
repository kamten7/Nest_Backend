package com.nest.constant;

/** 租房订单状态机常量。 */
public final class RentOrderStatus {

    private RentOrderStatus() {}

    public static final int PENDING_DEPOSIT = 1;
    public static final int RENTING = 2;
    public static final int TERMINATING = 3;
    public static final int TERMINATED = 4;
    public static final int CANCELLED = 5;

    public static final int[] DEPOSIT_LOCKED_STATUS = {RENTING, TERMINATING};

    public static String text(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PENDING_DEPOSIT -> "待缴押金";
            case RENTING -> "租房中";
            case TERMINATING -> "退租申请中";
            case TERMINATED -> "已退租";
            case CANCELLED -> "已取消";
            default -> "未知";
        };
    }
}
