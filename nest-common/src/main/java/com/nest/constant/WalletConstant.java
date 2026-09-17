package com.nest.constant;

import java.math.BigDecimal;

/** 钱包模块常量：业务类型 / 收付方向 / 流水状态 / 资金来源。 */
public final class WalletConstant {

    private WalletConstant() {}

    /** 单笔充值上限（元）—— 防止误输入 / 恶意构造天文数字 */
    public static final BigDecimal RECHARGE_AMOUNT_MAX = new BigDecimal("50000");

    public static final String BIZ_RECHARGE = "RECHARGE";
    public static final String BIZ_WITHDRAW = "WITHDRAW";
    public static final String BIZ_DEPOSIT_PAY = "DEPOSIT_PAY";
    public static final String BIZ_DEPOSIT_INCOME = "DEPOSIT_INCOME";
    public static final String BIZ_DEPOSIT_REFUND = "DEPOSIT_REFUND";
    public static final String BIZ_RENT_PAY = "RENT_PAY";
    public static final String BIZ_RENT_INCOME = "RENT_INCOME";

    public static final int DIRECTION_IN = 1;
    public static final int DIRECTION_OUT = -1;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    public static final String SOURCE_SIMULATE = "SIMULATE";
    public static final String SOURCE_WECHAT_PAY = "WECHAT_PAY";

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
