package com.nest.vo;

import lombok.Data;

/**
 * 钱包转账结果视图（押金/房租收付两侧流水 ID）。
 */
@Data
public class WalletTransferVO {

    /** 付款方（租客）流水 ID */
    private Long payerTxnId;
    /** 收款方（房东）流水 ID */
    private Long payeeTxnId;
}
