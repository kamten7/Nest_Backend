package com.nest.constant;

/** 租房订单相关常量：支付类型 / 支付方式 / 业务规则上限。 */
public final class RentConstant {

    private RentConstant() {}

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

    public static final String PAY_METHOD_WALLET = "WALLET";
    public static final String PAY_METHOD_WECHAT_PAY = "WECHAT_PAY";

    public static final String ORDER_NO_PREFIX = "RO";
    public static final String BIZ_PREFIX_DEPOSIT = "DEP";
    public static final String BIZ_PREFIX_RENT = "RNT";

    public static final int MAX_AHEAD_MONTHS = 5;
    public static final int REMIND_BEFORE_DAYS = 3;
    public static final int SETTLE_GRACE_DAYS = 7;

    /**
     * 待缴押金超时阈值（分钟）。
     */
    public static final int DEPOSIT_PAY_TIMEOUT_MINUTES = 30;
}
