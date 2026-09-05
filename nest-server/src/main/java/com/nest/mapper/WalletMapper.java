package com.nest.mapper;

import com.nest.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 用户钱包 Mapper。
 */
@Mapper
public interface WalletMapper {

    /** 按用户查询钱包（一个用户一个） */
    Wallet selectByUser(@Param("userType") String userType, @Param("userId") Long userId);

    /** 按 ID 查询钱包 */
    Wallet selectById(@Param("id") Long id);

    /** 新增钱包，回填 ID */
    int insert(Wallet wallet);

    /** 原子加余额（balance = balance + amount） */
    int increaseBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** 原子扣减余额，余额足够才成功（balance = balance - amount AND balance >= amount） */
    int decreaseBalanceIfSufficient(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
