-- ============================================================
-- Nest 租房平台 — 租房订单模块表结构
-- 依赖库：nest_rent（需先建 wallet / wallet_transaction）
-- ============================================================
SET NAMES utf8mb4;
USE nest_rent;

-- ==================== 租房订单主表 ====================
CREATE TABLE IF NOT EXISTS rent_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_no VARCHAR(32) NOT NULL COMMENT '订单号',
    appointment_id BIGINT COMMENT '来源预约ID',
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    landlord_id BIGINT NOT NULL COMMENT '房东ID(房款入账对象)',
    deposit DECIMAL(12,2) NOT NULL COMMENT '押金(可退)',
    monthly_rent DECIMAL(12,2) NOT NULL COMMENT '月租',
    status TINYINT DEFAULT 1 COMMENT '状态 1待缴押金 2租房中 3退租申请中 4已退租 5已取消',
    start_date DATE COMMENT '起租日',
    next_due_period VARCHAR(7) COMMENT '下次待缴周期 yyyy-MM',
    paid_months INT DEFAULT 0 COMMENT '已缴月数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_tenant (tenant_id),
    INDEX idx_landlord (landlord_id),
    INDEX idx_house (house_id)
) COMMENT '租房订单表';

-- ==================== 租金/押金支付记录 ====================
CREATE TABLE IF NOT EXISTS rent_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '所属订单ID',
    pay_type VARCHAR(20) NOT NULL COMMENT '支付类型 DEPOSIT/RENT',
    period VARCHAR(7) COMMENT '租金所属周期(押金为空)',
    amount DECIMAL(12,2) NOT NULL COMMENT '支付金额',
    pay_method VARCHAR(20) DEFAULT 'WALLET' COMMENT '支付方式 WALLET/WECHAT_PAY',
    status TINYINT DEFAULT 1 COMMENT '状态 1成功 0处理中',
    biz_no VARCHAR(32) COMMENT '业务号(提前支付多笔共享)',
    tenant_txn_id BIGINT COMMENT '租客侧钱包流水ID',
    landlord_txn_id BIGINT COMMENT '房东侧钱包流水ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order (order_id)
) COMMENT '租房支付记录表';

-- ==================== 退租申请记录 ====================
CREATE TABLE IF NOT EXISTS rent_termination (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '所属订单ID',
    tenant_id BIGINT NOT NULL COMMENT '退租申请人(租客)',
    apply_time DATETIME NOT NULL COMMENT '退租申请时间',
    effective_end_period VARCHAR(7) NOT NULL COMMENT '生效的已购租期末周期',
    refund_status TINYINT DEFAULT 0 COMMENT '押金退回 0待退 1已退',
    refund_time DATETIME COMMENT '押金实际退回时间',
    refund_txn_id BIGINT COMMENT '押金退回流水ID',
    remark VARCHAR(200) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order (order_id),
    INDEX idx_refund (refund_status)
) COMMENT '退租申请记录表';

-- ==================== 房租提醒去重日志 ====================
CREATE TABLE IF NOT EXISTS rent_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '被提醒订单ID',
    remind_period VARCHAR(7) NOT NULL COMMENT '提醒针对的待缴周期',
    remind_date DATE NOT NULL COMMENT '提醒日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_order_period (order_id, remind_period, remind_date)
) COMMENT '房租提醒去重日志表';
