-- ============================================================================
-- Nest 安居 · MySQL 8.0 建表脚本（唯一 DDL 文件）
-- ----------------------------------------------------------------------------
-- 用法（新库，先建库再导入）：
--   mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS nest_rent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
--   mysql -uroot -p nest_rent < db/schema.sql
--   mysql -uroot -p nest_rent < db/data.sql
-- 或在 docker compose 中自动执行（挂载到 /docker-entrypoint-initdb.d，容器已通过 MYSQL_DATABASE 建库）。
--
-- 说明：
--   * 24 张表：业务 20 + 知识库 2 + ai_trace + shedlock
--   * 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行；不包含 DROP DATABASE
--   * pgvector 是 PostgreSQL，不是 MySQL，初始化见 db/postgres/01_rag.sql
--   * 旧库若已有 landlord 表但缺 role 列，脚本末尾会幂等补列
-- ============================================================================

-- 强制客户端/连接使用 utf8mb4，避免 Docker init 时中文被按 latin1 写入
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS landlord (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '房东姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    avatar VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    password VARCHAR(64) NOT NULL COMMENT '密码(BCrypt，兼容历史MD5摘要)',
    id_number VARCHAR(18) DEFAULT NULL COMMENT '身份证号',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    role VARCHAR(20) NOT NULL DEFAULT 'LANDLORD' COMMENT '角色：LANDLORD 普通房东 / PLATFORM_ADMIN 平台管理员',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房东表';

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
    UNIQUE KEY uk_openid (openid),
    UNIQUE KEY uk_tenant_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租客表';

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
    status TINYINT DEFAULT 1 COMMENT '状态 1上架 2在租中 0下架(需房东重新发布)',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_landlord (landlord_id),
    KEY idx_city_district (city, district),
    KEY idx_price (price),
    KEY idx_status (status),
    KEY idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源表';

CREATE TABLE IF NOT EXISTS house_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    url VARCHAR(500) NOT NULL COMMENT '图片URL(MinIO)',
    is_cover TINYINT DEFAULT 0 COMMENT '是否封面图 1是 0否',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源图片表';

CREATE TABLE IF NOT EXISTS house_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    tag_name VARCHAR(30) NOT NULL COMMENT '标签名(近地铁/朝南/可短租/有电梯/精装修等)',
    KEY idx_house (house_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源标签表';

CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tenant_house (tenant_id, house_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏表';

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

CREATE TABLE IF NOT EXISTS review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '发帖用户(租客)ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    rating TINYINT NULL COMMENT '评分 1-5（NULL=未打分的纯评论）',
    content TEXT COMMENT '评论内容',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_house (house_id),
    KEY idx_tenant (tenant_id),
    KEY idx_house_time (house_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源评论表';

CREATE TABLE IF NOT EXISTS review_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '评价ID',
    user_type VARCHAR(10) NOT NULL COMMENT '点赞者类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '点赞者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_review_user (review_id, user_type, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='顶楼评价点赞表';

CREATE TABLE IF NOT EXISTS review_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '所属评论ID',
    user_type VARCHAR(10) NOT NULL COMMENT '评论者类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '评论者ID',
    content TEXT NOT NULL COMMENT '回复内容',
    parent_id BIGINT DEFAULT NULL COMMENT '父回复ID(NULL=一级回复)',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_review (review_id),
    KEY idx_review_time (review_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复表(支持嵌套)';

CREATE TABLE IF NOT EXISTS review_comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL COMMENT '回复ID',
    user_type VARCHAR(10) NOT NULL COMMENT '点赞者类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '点赞者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_comment_user (comment_id, user_type, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论回复点赞表';

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

CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_type VARCHAR(10) NOT NULL COMMENT '发送者类型 tenant/landlord',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    msg_type VARCHAR(20) DEFAULT 'text' COMMENT '消息类型 text/image',
    client_msg_id VARCHAR(64) DEFAULT NULL COMMENT '客户端幂等键：断线重发去重（NULL 不受唯一约束）',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读 1是 0否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_conversation (conversation_id),
    KEY idx_sender (sender_type, sender_id),
    UNIQUE KEY uk_client_msg (sender_type, sender_id, client_msg_id) COMMENT '幂等兜底：同一发送者同一 clientMsgId 只落一条'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';

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
    idem_key VARCHAR(64) DEFAULT NULL COMMENT '幂等键(客户端生成；提现防重，双击/重试只受理一次)',
    peer_txn_id BIGINT DEFAULT NULL COMMENT '对端流水ID(同笔转账双端关联)',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user (user_type, user_id),
    KEY idx_biz (biz_no),
    UNIQUE KEY uk_idem (idem_key) COMMENT '幂等兜底：同键只能落一条流水（NULL 不受约束，非提现场景全部为 NULL）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包流水表';

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
    KEY idx_house (house_id),
    KEY idx_status_create (status, create_time) COMMENT '状态+时间复合：定时任务按状态扫表（押金超时/退款/提醒）不再全表扫'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租房订单表';

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
    UNIQUE KEY uk_order_type_period (order_id, pay_type, period) COMMENT '同单同类型同周期唯一：堵并发重复缴租（period 为 NULL 的押金不受限）',
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租房支付记录表';

CREATE TABLE IF NOT EXISTS rent_termination (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '所属订单ID',
    tenant_id BIGINT NOT NULL COMMENT '退租申请人(租客)',
    apply_time DATETIME NOT NULL COMMENT '退租申请时间（结算冷却期从此刻起算）',
    effective_end_period VARCHAR(7) NOT NULL COMMENT '退租生效期(yyyy-MM)：申请当月，视为已住满不退',
    deduct_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '结算时从押金中扣除、归房东的金额(物品损坏等)',
    refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实际退回租客的押金金额(= 订单押金 - deduct_amount)',
    prepaid_months INT NOT NULL DEFAULT 0 COMMENT '退租时未消耗的已预付整月数(生效期之后)',
    prepaid_refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预付租金退回金额(= prepaid_months × 月租)',
    refund_status TINYINT DEFAULT 0 COMMENT '押金退回 0待退 1已退',
    refund_time DATETIME DEFAULT NULL COMMENT '押金实际退回时间',
    refund_txn_id BIGINT DEFAULT NULL COMMENT '押金退回流水ID',
    prepaid_txn_id BIGINT DEFAULT NULL COMMENT '预付租金退回流水ID',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_order (order_id),
    KEY idx_refund (refund_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退租申请记录表';

CREATE TABLE IF NOT EXISTS rent_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '被提醒订单ID',
    remind_period VARCHAR(7) NOT NULL COMMENT '提醒针对的待缴周期',
    remind_date DATE NOT NULL COMMENT '提醒日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_order_period (order_id, remind_period, remind_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房租提醒去重日志表';

CREATE TABLE IF NOT EXISTS notify_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL COMMENT '通知类型: appointment/comment/like/notice',
    user_type VARCHAR(16) NOT NULL COMMENT '目标用户端: tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '目标用户 ID',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待重试 1已送达 2放弃',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, retry_count, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送失败补偿任务';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL COMMENT '文档标题',
    category VARCHAR(50) NOT NULL COMMENT '分类：policy/faq/city/contract/safety/process/fees',
    source_type VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN' COMMENT '来源类型 MARKDOWN/TEXT/UPLOAD',
    source_uri VARCHAR(500) DEFAULT NULL COMMENT '来源路径或文件名',
    city VARCHAR(50) DEFAULT NULL COMMENT '适用城市（NULL=通用）',
    district VARCHAR(50) DEFAULT NULL COMMENT '适用区域（NULL=不限）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1索引中 2已索引 3失败',
    chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数',
    content_hash CHAR(64) NOT NULL COMMENT '原始内容 SHA-256，幂等去重',
    version INT NOT NULL DEFAULT 1 COMMENT '索引版本，reindex +1',
    error_msg VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    content MEDIUMTEXT NOT NULL COMMENT '原始文本，reindex 不依赖原文件',
    created_by BIGINT DEFAULT NULL COMMENT '上传者（房东端用户ID）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_hash (content_hash),
    KEY idx_status (status),
    KEY idx_category_city (category, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库文档表';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '所属文档ID',
    chunk_index INT NOT NULL COMMENT '分块序号（从0开始）',
    content TEXT NOT NULL COMMENT '分块文本',
    content_hash CHAR(64) NOT NULL COMMENT '分块内容 SHA-256',
    embedding_id CHAR(36) NOT NULL COMMENT 'pgvector 中的 embedding_id',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_chunk (doc_id, chunk_index),
    UNIQUE KEY uk_embedding (embedding_id),
    KEY idx_doc (doc_id),
    FULLTEXT KEY ft_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库分块表';

CREATE TABLE IF NOT EXISTS ai_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id CHAR(36) NOT NULL COMMENT '链路 ID（SSE 响应头 X-Trace-Id）',
    tenant_id BIGINT DEFAULT NULL COMMENT '租客ID',
    channel VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '通道 user/admin',
    question_preview VARCHAR(200) DEFAULT NULL COMMENT '问题前 200 字（不存全量）',
    tool_calls TEXT DEFAULT NULL COMMENT '工具调用 JSON：[{name,args,durationMs,success}]',
    retrieved_docs TEXT DEFAULT NULL COMMENT '检索来源 JSON（预留）',
    latency_ms INT DEFAULT NULL COMMENT '总耗时（毫秒）',
    prompt_tokens INT DEFAULT NULL COMMENT '输入 token',
    completion_tokens INT DEFAULT NULL COMMENT '输出 token',
    model VARCHAR(100) DEFAULT NULL COMMENT '模型名',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_code VARCHAR(100) DEFAULT NULL COMMENT '错误码/摘要',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trace (trace_id),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 对话轨迹表';

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ShedLock 分布式定时任务锁';

-- ----------------------------------------------------------------------------
-- 兼容旧库：landlord 表已存在但缺少 role 列时补列（新库由 CREATE TABLE 直接带出）
-- ----------------------------------------------------------------------------
SET @role_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'landlord' AND COLUMN_NAME = 'role'
);
SET @ddl := IF(@role_exists = 0,
    'ALTER TABLE landlord ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT ''LANDLORD'' COMMENT ''角色：LANDLORD 普通房东 / PLATFORM_ADMIN 平台管理员''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
