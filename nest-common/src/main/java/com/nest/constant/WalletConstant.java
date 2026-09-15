package com.nest.constant;

/**
 * 钱包模块常量：业务类型 / 收付方向 / 流水状态 / 资金来源。
 */
public final class WalletConstant {

    private WalletConstant() {}

    // ==================== 业务类型 biz_type ====================
    /** 充值 */
    public static final String BIZ_RECHARGE = "RECHARGE";
    /** 提现 */
    public static final String BIZ_WITHDRAW = "WITHDRAW";
    /** 租客缴押金（支出） */
    public static final String BIZ_DEPOSIT_PAY = "DEPOSIT_PAY";
    /** 房东收押金（收入） */
    public static final String BIZ_DEPOSIT_INCOME = "DEPOSIT_INCOME";
    /** 押金退回（租客收入 / 房东支出） */
    public static final String BIZ_DEPOSIT_REFUND = "DEPOSIT_REFUND";
    /** 租客缴租（支出） */
    public static final String BIZ_RENT_PAY = "RENT_PAY";
    /** 房东收租（收入） */
    public static final String BIZ_RENT_INCOME = "RENT_INCOME";

    // ==================== 收付方向 direction ====================
    /** 收入（余额 +） */
    public static final int DIRECTION_IN = 1;
    /** 支出（余额 −） */
    public static final int DIRECTION_OUT = -1;

    // ==================== 流水状态 status ====================
    /** 处理中（提现待打款） */
    public static final int STATUS_PENDING = 0;
    /** 成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 失败 */
    public static final int STATUS_FAILED = 2;

    // ==================== 资金来源 source ====================
    /** 模拟（本期使用） */
    public static final String SOURCE_SIMULATE = "SIMULATE";
    /** 微信支付（后期接入） */
    public static final String SOURCE_WECHAT_PAY = "WECHAT_PAY";

    /**
     * 业务类型中文文本（与前端 BIZ_TEXT 映射保持一致）。
     */
    public static String bizText(String bizType) {
        if (bizType == null) {
            return "";
        }
        return switch (bizType) {
            case BIZ_RECHARGE -> "充值";
            case BIZ_WITHDRAW -> "提现";
            case BIZ_DEPOSIT_PAY -> "缴纳押金";
            case BIZ_DEPOSIT_INCOME -> "收取押金";
            case BIZ_DEPOSIT_REFUND -> "押金退回";
            case BIZ_RENT_PAY -> "缴纳房租";
            case BIZ_RENT_INCOME -> "收取房租";
            default -> bizType;
        };
    }
}
