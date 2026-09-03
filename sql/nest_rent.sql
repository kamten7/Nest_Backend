-- ============================================================
-- Nest 租房平台 — 数据库初始化脚本
-- 数据库名：nest_rent
-- ============================================================

-- 强制客户端字符集，防止中文乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS nest_rent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE nest_rent;

-- ==================== 房东表 ====================
CREATE TABLE IF NOT EXISTS landlord (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '房东姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    avatar VARCHAR(500) COMMENT '头像URL',
    password VARCHAR(64) NOT NULL COMMENT '密码(MD5摘要)',
    id_number VARCHAR(18) COMMENT '身份证号',
    status TINYINT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_phone (phone)
) COMMENT '房东表';

-- ==================== 租客表 ====================
CREATE TABLE IF NOT EXISTS tenant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(50) COMMENT '微信openid',
    nickname VARCHAR(50) COMMENT '昵称',
    avatar VARCHAR(500) COMMENT '头像URL',
    phone VARCHAR(20) COMMENT '手机号',
    gender TINYINT COMMENT '性别 1男 2女 0未知',
    status TINYINT DEFAULT 1 COMMENT '状态 1正常 0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_openid (openid)
) COMMENT '租客表';

-- ==================== 房源表 ====================
CREATE TABLE IF NOT EXISTS house (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    landlord_id BIGINT NOT NULL COMMENT '房东ID',
    title VARCHAR(100) NOT NULL COMMENT '房源标题',
    description TEXT COMMENT '房源描述',
    address VARCHAR(200) NOT NULL COMMENT '详细地址',
    province VARCHAR(20) COMMENT '省',
    city VARCHAR(20) COMMENT '市',
    district VARCHAR(20) COMMENT '区',
    latitude DECIMAL(10,7) COMMENT '纬度',
    longitude DECIMAL(10,7) COMMENT '经度',
    price DECIMAL(10,2) NOT NULL COMMENT '月租金(元)',
    deposit DECIMAL(10,2) COMMENT '押金(元)',
    area DECIMAL(8,2) COMMENT '面积(㎡)',
    room_count TINYINT COMMENT '室',
    hall_count TINYINT COMMENT '厅',
    bathroom_count TINYINT COMMENT '卫',
    floor TINYINT COMMENT '楼层',
    total_floor TINYINT COMMENT '总楼层',
    orientation VARCHAR(10) COMMENT '朝向(东/南/西/北/东南/西南/东北/西北)',
    rent_type VARCHAR(10) COMMENT '出租方式(整租/合租/短租)，默认整租由应用层处理',
    available_date DATE COMMENT '可入住日期',
    utilities VARCHAR(100) COMMENT '水电燃气说明',
    requirements VARCHAR(200) COMMENT '租客要求',
    status TINYINT DEFAULT 1 COMMENT '状态 1上架 0下架',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_landlord (landlord_id),
    INDEX idx_city_district (city, district),
    INDEX idx_price (price),
    INDEX idx_status (status),
    INDEX idx_location (latitude, longitude)
) COMMENT '房源表';

-- ==================== 房源图片表 ====================
CREATE TABLE IF NOT EXISTS house_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    url VARCHAR(500) NOT NULL COMMENT '图片URL(MinIO)',
    is_cover TINYINT DEFAULT 0 COMMENT '是否封面图 1是 0否',
    sort_order INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_house (house_id)
) COMMENT '房源图片表';

-- ==================== 房源标签表 ====================
CREATE TABLE IF NOT EXISTS house_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    house_id BIGINT NOT NULL COMMENT '房源ID',
    tag_name VARCHAR(30) NOT NULL COMMENT '标签名(近地铁/朝南/可短租/有电梯/精装修等)',
    INDEX idx_house (house_id)
) COMMENT '房源标签表';

-- ==================== 收藏表 ====================
CREATE TABLE IF NOT EXISTS favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_tenant_house (tenant_id, house_id),
    INDEX idx_tenant (tenant_id)
) COMMENT '收藏表';

-- ==================== 预约看房表 ====================
CREATE TABLE IF NOT EXISTS appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '租客ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    landlord_id BIGINT NOT NULL COMMENT '房东ID',
    contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    appointment_time DATETIME COMMENT '期望看房时间',
    remark VARCHAR(200) COMMENT '备注',
    status TINYINT DEFAULT 1 COMMENT '状态 1待确认 2已确认 3已看房 4已取消 5已成交',
    cancel_reason VARCHAR(200) COMMENT '取消原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant (tenant_id),
    INDEX idx_landlord (landlord_id),
    INDEX idx_house (house_id)
) COMMENT '预约看房表';

-- ==================== 评论表 ====================
CREATE TABLE IF NOT EXISTS review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT '评论者(租客)ID',
    house_id BIGINT NOT NULL COMMENT '房源ID',
    rating TINYINT NOT NULL COMMENT '评分 1-5',
    content TEXT COMMENT '评论内容',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_house (house_id),
    INDEX idx_tenant (tenant_id),
    UNIQUE KEY uk_tenant_house (tenant_id, house_id) COMMENT '每租客每房源限评一次'
) COMMENT '房源评论表';

-- ==================== 评论回复表 ====================
CREATE TABLE IF NOT EXISTS review_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '所属评论ID',
    user_type VARCHAR(10) NOT NULL COMMENT '评论者类型 tenant/landlord',
    user_id BIGINT NOT NULL COMMENT '评论者ID',
    content TEXT NOT NULL COMMENT '回复内容',
    parent_id BIGINT COMMENT '父回复ID(NULL=一级回复)',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_review (review_id)
) COMMENT '评论回复表(支持嵌套)';

-- ==================== 评论点赞表 ====================
CREATE TABLE IF NOT EXISTS review_comment_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL COMMENT '回复ID',
    tenant_id BIGINT NOT NULL COMMENT '点赞用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_comment_tenant (comment_id, tenant_id)
) COMMENT '评论回复点赞表';

-- ==================== 会话表 ====================
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user1_type VARCHAR(10) NOT NULL COMMENT '参与者1类型 tenant/landlord',
    user1_id BIGINT NOT NULL COMMENT '参与者1 ID',
    user2_type VARCHAR(10) NOT NULL COMMENT '参与者2类型 tenant/landlord',
    user2_id BIGINT NOT NULL COMMENT '参与者2 ID',
    last_message TEXT COMMENT '最后一条消息摘要',
    last_message_time DATETIME COMMENT '最后消息时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_users (user1_type, user1_id, user2_type, user2_id)
) COMMENT '会话表';

-- ==================== 消息表 ====================
CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID',
    sender_type VARCHAR(10) NOT NULL COMMENT '发送者类型 tenant/landlord',
    sender_id BIGINT NOT NULL COMMENT '发送者ID',
    content TEXT NOT NULL COMMENT '消息内容',
    msg_type VARCHAR(20) DEFAULT 'text' COMMENT '消息类型 text/image',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读 1是 0否',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation (conversation_id),
    INDEX idx_sender (sender_type, sender_id)
) COMMENT '聊天消息表';
