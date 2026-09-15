package com.nest.order.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.wallet.service.LockedAmountProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 押金锁定金额提供方实现。 */
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
