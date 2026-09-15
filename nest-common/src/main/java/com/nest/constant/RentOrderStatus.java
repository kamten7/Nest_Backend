package com.nest.constant;

/**
 * 租房订单状态机常量。
 *
 * <p>1 待缴押金 → 2 租房中 → 3 退租申请中 → 4 已退租；或 1 → 5 已取消。
 * 与前端 `miniapp/pages/rent` 的 STATUS_TEXT、以及 `frontend` 房东端保持一致。
 */
public final class RentOrderStatus {

    private RentOrderStatus() {}

    /** 待缴押金 */
    public static final int PENDING_DEPOSIT = 1;
    /** 租房中 */
    public static final int RENTING = 2;
    /** 退租申请中（已停止提醒与缴费，等租期结束结算押金） */
    public static final int TERMINATING = 3;
    /** 已退租（押金已结算） */
    public static final int TERMINATED = 4;
    /** 已取消（放弃租房） */
    public static final int CANCELLED = 5;

    /**
     * 占用房东押金额度的状态：钱还在房东钱包里、尚未结算退回。
     * 退租申请中同样占用 —— 钱还没退出去，房东依然不能动。
     */
    public static final int[] DEPOSIT_LOCKED_STATUS = {RENTING, TERMINATING};

    /** 状态文本。 */
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
