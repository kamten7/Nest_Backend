package com.nest.wallet.mapper;

import com.nest.entity.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 钱包流水 Mapper。 */
@Mapper
public interface WalletTransactionMapper {

    /** 写入一条流水 */
    int insert(WalletTransaction txn);

    /** 回填对端流水 ID（双向记账互指） */
    int updatePeerTxn(@Param("id") Long id, @Param("peerTxnId") Long peerTxnId);

    /** 按用户分页查询流水（bizType 可为空=全部） */
    List<WalletTransaction> selectByUser(@Param("userType") String userType,
                                         @Param("userId") Long userId,
                                         @Param("bizType") String bizType);
}
