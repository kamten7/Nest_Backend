package com.nest.wallet.mapper;

import com.nest.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/** 钱包 Mapper。 */
@Mapper
public interface WalletMapper {

    /** 按 (user_type,user_id) 查询钱包 */
    Wallet selectByUser(@Param("userType") String userType, @Param("userId") Long userId);

    /** 按 ID 查询 */
    Wallet selectById(@Param("id") Long id);

    /** 新建钱包 */
    int insert(Wallet wallet);

    /** 增加余额（收入 / 充值） */
    int increaseBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** 条件扣款：仅当余额充足时扣减，返回影响行数（0 表示余额不足）。 */
    int decreaseBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** 条件扣款（带锁定金额）：仅当「余额 − 锁定金额 ≥ 扣款金额」时扣减。 */
    int decreaseBalanceWithLock(@Param("id") Long id,
                                @Param("amount") BigDecimal amount,
                                @Param("lockedAmount") BigDecimal lockedAmount);
}
