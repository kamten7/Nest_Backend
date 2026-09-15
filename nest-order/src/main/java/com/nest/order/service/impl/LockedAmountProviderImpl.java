package com.nest.order.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.wallet.service.LockedAmountProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 押金锁定金额提供方实现。
 *
 * <p>规则：房东钱包余额里包含租客已缴的押金，但在租期内（{@link RentOrderStatus#RENTING}
 * 与 {@link RentOrderStatus#TERMINATING}）这部分钱不可提现。只有在退租结算完成后，
 * 订单转入「已退租」，押金才从锁定额里消失——此时被房东扣下的赔偿部分归房东、
 * 扣除部分已在钱包内变为可提现，退回租客的部分也已实际转出。
 *
 * <p>只对房东生效；租客侧无锁定（返回 0）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockedAmountProviderImpl implements LockedAmountProvider {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RentOrderMapper rentOrderMapper;

    @Override
    public BigDecimal lockedAmountOf(String userType, Long userId) {
        if (!JwtConstant.TYPE_LANDLORD.equals(userType) || userId == null) {
            return ZERO;
        }
        BigDecimal locked = rentOrderMapper.sumLockedDeposit(userId, RentOrderStatus.DEPOSIT_LOCKED_STATUS);
        if (locked == null) {
            return ZERO;
        }
        if (locked.signum() > 0) {
            log.debug("房东押金锁定额: landlordId={}, locked={}", userId, locked);
        }
        return locked;
    }
}
