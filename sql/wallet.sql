-- ============================================================
-- Nest 租房平台 — 钱包模块表结构
-- 依赖库：nest_rent
-- ============================================================
SET NAMES utf8mb4;
USE nest_rent;

-- ==================== 用户钱包表 ====================
CREATE TABLE IF NOT EXISTS wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_type VARCHAR(10) NOT NULL COMMENT '用户类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '用户ID(对应tenant.id/landlord.id)',
    balance DECIMAL(12,2) DEFAULT 0 COMMENT '当前余额',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0冻结',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user (user_type, user_id)
) COMMENT '用户钱包表';

-- ==================== 钱包流水表（双向记账） ====================
CREATE TABLE IF NOT EXISTS wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    wallet_id BIGINT NOT NULL COMMENT '钱包ID',
    user_type VARCHAR(10) NOT NULL COMMENT '用户类型 冗余便于查询',
    user_id BIGINT NOT NULL COMMENT '用户ID 冗余便于查询',
    biz_type VARCHAR(30) NOT NULL COMMENT '业务类型 RECHARGE/WITHDRAW/DEPOSIT_PAY/DEPOSIT_INCOME/DEPOSIT_REFUND/RENT_PAY/RENT_INCOME',
    amount DECIMAL(12,2) NOT NULL COMMENT '金额 恒为正',
    direction TINYINT NOT NULL COMMENT '方向 1收入 -1支出',
    balance_after DECIMAL(12,2) NOT NULL COMMENT '交易后余额快照',
    source VARCHAR(20) DEFAULT 'SIMULATE' COMMENT '资金来源 SIMULATE/WECHAT_PAY',
    status TINYINT DEFAULT 1 COMMENT '状态 1成功 0处理中 2失败',
    biz_no VARCHAR(32) COMMENT '业务单号 关联支付记录或少量共享',
    peer_txn_id BIGINT COMMENT '对端流水ID 同笔转账双端关联',
    remark VARCHAR(200) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_type, user_id),
    INDEX idx_biz (biz_no)
) COMMENT '钱包流水表';
