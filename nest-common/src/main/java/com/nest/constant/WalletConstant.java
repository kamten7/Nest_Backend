package com.nest.constant;

/**
 * 钱包模块相关常量：业务类型、方向、状态、资金来源。
 *
 * 说明：金额统一存正数，用 direction 区分收入/支出；
 * 业务类型是流水台账的枚举值，后期房租/押金也会用到。
 */
public final class WalletConstant {

    private WalletConstant() {}

    // ==================== 业务类型 ====================

    /** 充值 */
    public static final String BIZ_RECHARGE = "RECHARGE";
    /** 提现 */
    public static final String BIZ_WITHDRAW = "WITHDRAW";
    /** 租客缴纳押金 */
    public static final String BIZ_DEPOSIT_PAY = "DEPOSIT_PAY";
    /** 房东收取押金 */
    public static final String BIZ_DEPOSIT_INCOME = "DEPOSIT_INCOME";
    /** 押金退回 */
    public static final String BIZ_DEPOSIT_REFUND = "DEPOSIT_REFUND";
    /** 租客缴纳房租 */
    public static final String BIZ_RENT_PAY = "RENT_PAY";
    /** 房东收取房租 */
    public static final String BIZ_RENT_INCOME = "RENT_INCOME";

    // ==================== 方向 ====================

    /** 收入（加钱） */
    public static final int DIRECTION_IN = 1;
    /** 支出（扣钱） */
    public static final int DIRECTION_OUT = -1;

    // ==================== 流水状态 ====================

    /** 成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 处理中（提现待打款） */
    public static final int STATUS_PROCESSING = 0;
    /** 失败 */
    public static final int STATUS_FAILED = 2;

    // ==================== 资金来源 ====================

    /** 模拟充值（未接微信支付） */
    public static final String SOURCE_SIMULATE = "SIMULATE";
    /** 微信支付（预留） */
    public static final String SOURCE_WECHAT = "WECHAT_PAY";
}
