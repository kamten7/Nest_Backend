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

    /**
     * 按 ID 查询并加排他行锁（SELECT ... FOR UPDATE），须在事务内调用。
     * <p>用于「读取锁定金额 + 条件扣款」这类需要同一时点快照的场景：先锁住钱包行，
     * 后续重读的值才不会被并发事务（如租客缴押金给房东加余额）插入中间态。</p>
     */
    Wallet lockById(@Param("id") Long id);

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
