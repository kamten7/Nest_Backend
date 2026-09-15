package com.nest.constant;

/**
 * 租房订单相关常量：支付类型 / 支付方式 / 业务规则上限。
 */
public final class RentConstant {

    private RentConstant() {}

    // ==================== 支付类型 pay_type ====================
    public static final String PAY_TYPE_DEPOSIT = "DEPOSIT";
    public static final String PAY_TYPE_RENT = "RENT";

    public static String payTypeText(String payType) {
        if (payType == null) {
            return "";
        }
        return switch (payType) {
            case PAY_TYPE_DEPOSIT -> "押金";
            case PAY_TYPE_RENT -> "租金";
            default -> payType;
        };
    }

    // ==================== 支付方式 pay_method ====================
    /** 钱包余额支付（本期） */
    public static final String PAY_METHOD_WALLET = "WALLET";
    /** 微信支付（预留，本期不接） */
    public static final String PAY_METHOD_WECHAT_PAY = "WECHAT_PAY";

    // ==================== 订单号 / 业务号前缀 ====================
    public static final String ORDER_NO_PREFIX = "RO";
    /** 押金支付批次 */
    public static final String BIZ_PREFIX_DEPOSIT = "DEP";
    /** 租金支付批次（提前支付 N 期共享同一批次号） */
    public static final String BIZ_PREFIX_RENT = "RNT";

    // ==================== 业务规则 ====================
    /** 单次提前支付月数上限 */
    public static final int MAX_AHEAD_MONTHS = 5;
    /** 待缴周期起始日提前几天提醒 */
    public static final int REMIND_BEFORE_DAYS = 3;
    /** 租期结束后给房东的结算宽限期（天），超期自动全额退押金 */
    public static final int SETTLE_GRACE_DAYS = 7;
}
