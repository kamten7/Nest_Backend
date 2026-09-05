package com.nest.constant;

/**
 * 租房订单模块常量：订单状态、支付类型、业务规则。
 *
 * 状态机：待缴押金(1) → 租房中(2) → 退租申请中(3) → 已退租(4)
 *        待缴押金(1) → 已取消(5)
 */
public final class RentConstant {

    private RentConstant() {}

    // ==================== 订单状态 ====================

    /** 待缴押金 */
    public static final int ORDER_PENDING_DEPOSIT = 1;
    /** 租房中 */
    public static final int ORDER_RENTING = 2;
    /** 退租申请中（租期末、停止提醒与缴费） */
    public static final int ORDER_TERMINATING = 3;
    /** 已退租（押金已退） */
    public static final int ORDER_TERMINATED = 4;
    /** 已取消 */
    public static final int ORDER_CANCELLED = 5;

    // ==================== 支付类型 ====================

    /** 押金 */
    public static final String PAY_DEPOSIT = "DEPOSIT";
    /** 租金 */
    public static final String PAY_RENT = "RENT";

    // ==================== 业务规则 ====================

    /** 单次最多提前支付月数 */
    public static final int MAX_AHEAD_MONTHS = 5;
    /** 到期提醒：提前天数 */
    public static final int REMINDER_DAYS = 3;
}
