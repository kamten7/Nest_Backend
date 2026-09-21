package com.nest.order.service.impl;

import com.nest.constant.JwtConstant;
import com.nest.constant.RentOrderStatus;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.wallet.service.LockedAmountProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 锁定金额提供方实现：在租订单的押金 + 未消耗的预付租金。
 * 两者都已进房东余额，但都要留住以备退租退回，故一并锁定、不可提现。
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
        // 基准日由 Java 给出，与 terminate 冻结金额时用的是同一个时钟（SQL 里不得使用 CURDATE()）
        BigDecimal locked = rentOrderMapper.sumLockedAmount(userId, LocalDate.now(),
                RentOrderStatus.DEPOSIT_LOCKED_STATUS, RentOrderStatus.TERMINATING);
        if (locked == null) {
            return ZERO;
        }
        if (locked.signum() > 0) {
            log.debug("房东锁定金额(押金+预付租金): landlordId={}, locked={}", userId, locked);
        }
        return locked;
    }
}
