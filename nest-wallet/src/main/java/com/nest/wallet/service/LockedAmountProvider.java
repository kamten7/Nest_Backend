package com.nest.wallet.service;

import java.math.BigDecimal;

/** 锁定金额提供方（SPI）。 */
public interface LockedAmountProvider {

    /** 查询某用户「不可提现」的金额总额（元）。 */
    BigDecimal lockedAmountOf(String userType, Long userId);
}
