-- ============================================================
-- Nest 租房平台 — 数据库初始化脚本（完整版）
-- ------------------------------------------------------------
-- 用途：全新库一键初始化（删库后重建）。执行本文件即可得到
--       全部 18 张表 + 房东种子数据，无需再跑其它脚本。
--
-- 包含：
--   基础业务（12 张）：landlord / tenant / house / house_image / house_tag /
--                      favorite / appointment / review / review_comment /
--                      review_comment_like / conversation / message
--   钱包与租房（6 张）：wallet / wallet_transaction / rent_order /
--                      rent_payment / rent_termination / rent_reminder_log
--
-- 种子数据：仅「房东」2 条（密码 123456 的 MD5 摘要；首次登录会自动升级为 BCrypt）。
--           其余表不填充任何数据。
--
-- 约定：
--   * 字符集统一 utf8mb4 / 排序规则 utf8mb4_0900_ai_ci（MySQL 8 默认）。
--   * 表之间为「逻辑外键」，不建物理 FOREIGN KEY 约束（由应用层保证一致性）。
--   * 全部 CREATE TABLE IF NOT EXISTS，可重复执行（幂等）。
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS nest_rent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE nest_rent;

-- ==================== 房东表 ====================
CREATE TABLE IF NOT EXISTS landlord (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '房东姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    password VARCHAR(64) NOT NULL COMMENT '密码(BCrypt，兼容历史MD5摘要)',
    id_number VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房东表';

-- ==================== 租客表 ====================
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(50) DEFAULT NULL COMMENT '微信openid',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    gender TINYINT DEFAULT NULL COMMENT '性别 1男 2女 0未知',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租客表';

-- ==================== 房源表 ====================
CREATE TABLE IF NOT EXISTS house (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    landlord_id BIGINT NOT NULL COMMENT '房东ID',
    title VARCHAR(100) NOT NULL COMMENT '房源标题',
    description TEXT COMMENT '房源描述',
    address VARCHAR(200) NOT NULL COMMENT '详细地址',
    province VARCHAR(20) DEFAULT NULL COMMENT '省',
    city VARCHAR(20) DEFAULT NULL COMMENT '市',
    district VARCHAR(20) DEFAULT NULL COMMENT '区',
    latitude DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
    longitude DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
    price DECIMAL(10,2) NOT NULL COMMENT '月租金(元)',
    deposit DECIMAL(10,2) DEFAULT NULL COMMENT '押金(元)',
    area DECIMAL(8,2) DEFAULT NULL COMMENT '面积(㎡)',
    room_count TINYINT DEFAULT NULL COMMENT '室',
    hall_count TINYINT DEFAULT NULL COMMENT '厅',
    bathroom_count TINYINT DEFAULT NULL COMMENT '卫',
    floor TINYINT DEFAULT NULL COMMENT '楼层',
    total_floor TINYINT DEFAULT NULL COMMENT '总楼层',
    orientation VARCHAR(10) DEFAULT NULL COMMENT '朝向(东/南/西/北/东南/西南/东北/西北)',
    rent_type VARCHAR(10) DEFAULT NULL COMMENT '出租方式(整租/合租/短租)，默认整租由应用层处理',
    available_date DATE DEFAULT NULL COMMENT '可入住日期',
    utilities VARCHAR(100) DEFAULT NULL COMMENT '水电燃气说明',
    requirements VARCHAR(200) DEFAULT NULL COMMENT '租客要求',
    status TINYINT DEFAULT 1 COMMENT '状态 1上架 0下架',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_landlord (landlord_id),
    KEY idx_city_district (city, district),
    KEY idx_price (price),
    KEY idx_status (status),
    KEY idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源表';

-- ==================== 房源图片表 ====================
CREATE TABLE IF NOT EXISTS house_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    url VARCHAR(500) NOT NULL COMMENT '图片URL(MinIO)',
    is_cover TINYINT DEFAULT 0 COMMENT '是否封面图 1是 0否',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源图片表';

-- ==================== 房源标签表 ====================
CREATE TABLE IF NOT EXISTS house_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    tag_name VARCHAR(30) NOT NULL COMMENT '标签名(近地铁/朝南/可短租/有电梯/精装修等)',
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源标签表';

-- ==================== 收藏表 ====================
CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tenant_house (tenant_id, house_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';

-- ==================== 预约看房表 ====================
CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    landlord_id BIGINT NOT NULL COMMENT '房东ID',
    contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    appointment_time DATETIME DEFAULT NULL COMMENT '期望看房时间',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    status TINYINT DEFAULT 1 COMMENT '状态 1待确认 2已确认 3已看房 4已取消 5已成交',
    cancel_reason VARCHAR(200) DEFAULT NULL COMMENT '取消原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_tenant (tenant_id),
    KEY idx_landlord (landlord_id),
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约看房表';

-- ==================== 房源评论表 ====================
CREATE TABLE IF NOT EXISTS review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '评论者(租客)ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    rating TINYINT NOT NULL COMMENT '评分 1-5',
    content TEXT COMMENT '评论内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tenant_house (tenant_id, house_id) COMMENT '每租客每房源限评一次',
    KEY idx_house (house_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源评论表';

-- ==================== 评论回复表 ====================
CREATE TABLE IF NOT EXISTS review_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '所属评论ID',
    user_type VARCHAR(10) NOT NULL COMMENT '评论者类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '评论者ID',
    content TEXT NOT NULL COMMENT '回复内容',
    parent_id BIGINT DEFAULT NULL COMMENT '父回复ID(NULL=一级回复)',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复表(支持嵌套)';

-- ==================== 评论回复点赞表 ====================
CREATE TABLE IF NOT EXISTS review_comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL COMMENT '回复ID',
    tenant_id BIGINT NOT NULL COMMENT '点赞用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_comment_tenant (comment_id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复点赞表';

-- ==================== 会话表 ====================
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user1_type VARCHAR(10) NOT NULL COMMENT '参与者1类型 tenant/landlord',
    user1_id BIGINT NOT NULL COMMENT '参与者1 ID',
    user2_type VARCHAR(10) NOT NULL COMMENT '参与者2类型 tenant/landlord',
    user2_id BIGINT NOT NULL COMMENT '参与者2 ID',
    last_message TEXT COMMENT '最后一条消息摘要',
    last_message_time DATETIME DEFAULT NULL COMMENT '最后消息时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_users (user1_type, user1_id, user2_type, user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话表';

-- ==================== 聊天消息表 ====================
CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_type VARCHAR(10) NOT NULL COMMENT '发送者类型 tenant/landlord',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    msg_type VARCHAR(20) DEFAULT 'text' COMMENT '消息类型 text/image',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读 1是 0否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_conversation (conversation_id),
    KEY idx_sender (sender_type, sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';

-- ==================== 用户钱包表（租客/房东共用） ====================
CREATE TABLE IF NOT EXISTS wallet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_type VARCHAR(10) NOT NULL COMMENT '用户类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '用户ID(对应tenant.id/landlord.id)',
    balance DECIMAL(12,2) DEFAULT 0 COMMENT '当前余额(元)',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0冻结',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user (user_type, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户钱包表';

-- ==================== 钱包流水表（双向记账） ====================
CREATE TABLE IF NOT EXISTS wallet_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL COMMENT '钱包ID',
    user_type VARCHAR(10) NOT NULL COMMENT '用户类型(冗余，便于查询)',
    user_id BIGINT NOT NULL COMMENT '用户ID(冗余，便于查询)',
    biz_type VARCHAR(30) NOT NULL COMMENT '业务类型 RECHARGE/WITHDRAW/DEPOSIT_PAY/DEPOSIT_INCOME/DEPOSIT_REFUND/RENT_PAY/RENT_INCOME',
    amount DECIMAL(12,2) NOT NULL COMMENT '金额(恒为正，方向由direction表达)',
    direction TINYINT NOT NULL COMMENT '方向 1收入 -1支出',
    balance_after DECIMAL(12,2) NOT NULL COMMENT '交易后余额快照',
    source VARCHAR(20) DEFAULT 'SIMULATE' COMMENT '资金来源 SIMULATE/WECHAT_PAY',
    status TINYINT DEFAULT 1 COMMENT '状态 1成功 0处理中 2失败',
    biz_no VARCHAR(32) DEFAULT NULL COMMENT '业务单号(关联支付记录，同批次共享)',
    peer_txn_id BIGINT DEFAULT NULL COMMENT '对端流水ID(同笔转账双端关联)',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user (user_type, user_id),
    KEY idx_biz (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包流水表';

-- ==================== 租房订单表 ====================
CREATE TABLE IF NOT EXISTS rent_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL COMMENT '订单号',
    appointment_id BIGINT DEFAULT NULL COMMENT '来源预约ID',
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    landlord_id BIGINT NOT NULL COMMENT '房东ID(房款入账对象)',
    deposit DECIMAL(12,2) NOT NULL COMMENT '押金(可退)',
    monthly_rent DECIMAL(12,2) NOT NULL COMMENT '月租',
    status TINYINT DEFAULT 1 COMMENT '状态 1待缴押金 2租房中 3退租申请中 4已退租 5已取消',
    start_date DATE DEFAULT NULL COMMENT '起租日',
    next_due_period VARCHAR(7) DEFAULT NULL COMMENT '下次待缴周期 yyyy-MM',
    paid_months INT DEFAULT 0 COMMENT '已缴月数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_tenant (tenant_id),
    KEY idx_landlord (landlord_id),
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租房订单表';

-- ==================== 租房支付记录表 ====================
CREATE TABLE IF NOT EXISTS rent_payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '所属订单ID',
    pay_type VARCHAR(20) NOT NULL COMMENT '支付类型 DEPOSIT/RENT',
    period VARCHAR(7) DEFAULT NULL COMMENT '租金所属周期(押金为空)',
    amount DECIMAL(12,2) NOT NULL COMMENT '支付金额',
    pay_method VARCHAR(20) DEFAULT 'WALLET' COMMENT '支付方式 WALLET/WECHAT_PAY',
    status TINYINT DEFAULT 1 COMMENT '状态 1成功 0处理中',
    biz_no VARCHAR(32) DEFAULT NULL COMMENT '业务号(提前支付多笔共享)',
    tenant_txn_id BIGINT DEFAULT NULL COMMENT '租客侧钱包流水ID',
    landlord_txn_id BIGINT DEFAULT NULL COMMENT '房东侧钱包流水ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租房支付记录表';

-- ==================== 退租申请记录表 ====================
CREATE TABLE IF NOT EXISTS rent_termination (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '所属订单ID',
    tenant_id BIGINT NOT NULL COMMENT '退租申请人(租客)',
    apply_time DATETIME NOT NULL COMMENT '退租申请时间',
    effective_end_period VARCHAR(7) NOT NULL COMMENT '生效的已购租期末周期',
    refund_status TINYINT DEFAULT 0 COMMENT '押金退回 0待退 1已退',
    refund_time DATETIME DEFAULT NULL COMMENT '押金实际退回时间',
    refund_txn_id BIGINT DEFAULT NULL COMMENT '押金退回流水ID',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_order (order_id),
    KEY idx_refund (refund_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退租申请记录表';

-- ==================== 房租提醒去重日志表 ====================
CREATE TABLE IF NOT EXISTS rent_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '被提醒订单ID',
    remind_period VARCHAR(7) NOT NULL COMMENT '提醒针对的待缴周期',
    remind_date DATE NOT NULL COMMENT '提醒日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_order_period (order_id, remind_period, remind_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房租提醒去重日志表';

-- ============================================================
-- 种子数据：房东账号（密码均为 123456）
--   password 存的是 MD5 摘要；房东登录时校验通过会自动升级为 BCrypt。
--   INSERT IGNORE + uk_phone 保证可重复执行不报错。
-- ============================================================
INSERT IGNORE INTO landlord (id, name, phone, password, status) VALUES
    (1, '张房东', '13800000001', 'e10adc3949ba59abbe56e057f20f883e', 1),
    (2, '李房东', '13800000002', 'e10adc3949ba59abbe56e057f20f883e', 1);
