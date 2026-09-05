package com.nest.mapper;

import com.nest.entity.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 钱包流水 Mapper。
 */
@Mapper
public interface WalletTransactionMapper {

    /** 新增流水，回填 ID */
    int insert(WalletTransaction txn);

    /** 回填对端流水 ID（同笔转账双端关联） */
    int updatePeerTxn(@Param("id") Long id, @Param("peerTxnId") Long peerTxnId);

    /** 查询某用户流水（分页由 PageHelper 处理），可按业务类型过滤 */
    List<WalletTransaction> selectByUser(@Param("userType") String userType,
                                         @Param("userId") Long userId,
                                         @Param("bizType") String bizType);
}
