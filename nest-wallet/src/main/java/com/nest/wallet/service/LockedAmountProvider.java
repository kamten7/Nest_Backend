package com.nest.wallet.service;

import java.math.BigDecimal;

/**
 * 锁定金额提供方（SPI）。
 *
 * <p>钱包只知道「余额」，不知道业务上哪些钱被冻结了。押金就是典型场景：
 * 押金收取后确实留在房东钱包里（余额可见），但在租期内房东不能提现，
 * 只有租客退租结算后，被扣下的部分才转为可提现。
 *
 * <p>为了不让 nest-wallet 反向依赖 nest-order，这里只声明接口，
 * 由 nest-order 提供实现（见 {@code LockedAmountProviderImpl}）。
 * 若应用未引入实现方（例如只跑钱包模块的单元测试），则视为无锁定金额。
 */
public interface LockedAmountProvider {

    /**
     * 查询某用户「不可提现」的金额总额（元）。
     *
     * @param userType 用户类型，见 {@code JwtConstant.TYPE_*}
     * @param userId   用户 ID
     * @return 锁定金额；无锁定时返回 {@link BigDecimal#ZERO}，永不返回 null
     */
    BigDecimal lockedAmountOf(String userType, Long userId);
}
