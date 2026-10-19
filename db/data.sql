-- ============================================================================
-- Nest 安居 · 演示数据初始化（唯一数据文件，MySQL）
-- ----------------------------------------------------------------------------
-- 用法：mysql -uroot -p nest_rent < db/data.sql
-- 或在 docker compose 中由 MySQL 容器自动执行（在 schema.sql 之后）。
-- 全部使用 INSERT IGNORE + 固定 ID，可重复执行；生产不要执行。
-- ============================================================================

-- 强制客户端/连接使用 utf8mb4，避免 Docker init 时中文被按 latin1 写入
SET NAMES utf8mb4;

-- ============================================================================
-- Nest 演示数据
-- ----------------------------------------------------------------------------
-- 演示/本地初始化数据；生产不要执行。
-- 全部使用 INSERT IGNORE + 固定 ID，重复执行不会报错、不会覆盖已有数据。
-- 图片使用 picsum.photos 占位图，仅用于本地演示；生产请替换为 MinIO 上的真实图片。
-- ============================================================================

-- 房东（密码 123456 的 MD5；首次登录会自动升级为 BCrypt）
INSERT IGNORE INTO landlord (id, name, phone, password, status) VALUES
    (1, '张房东', '13800000001', 'e10adc3949ba59abbe56e057f20f883e', 1),
    (2, '李房东', '13800000002', 'e10adc3949ba59abbe56e057f20f883e', 1);

-- 演示管理员：知识库 / AI 运营接口需要 PLATFORM_ADMIN 角色
UPDATE landlord SET role = 'PLATFORM_ADMIN' WHERE id = 1;

-- 租客：与 application-demo.yml 的 nest.wechat.mock-openid 保持一致
INSERT IGNORE INTO tenant (id, openid, nickname, phone, gender, status) VALUES
    (1, 'nest_demo_tenant', '演示租客', '13900000001', 0, 1);

-- 钱包（演示余额；生产不要预置余额）
INSERT IGNORE INTO wallet (id, user_type, user_id, balance, status) VALUES
    (1, 'landlord', 1, 0.00, 1),
    (2, 'tenant', 1, 5000.00, 1);

-- 房源
INSERT IGNORE INTO house (id, landlord_id, title, description, address, province, city, district,
    latitude, longitude, price, deposit, area, room_count, hall_count, bathroom_count,
    floor, total_floor, orientation, rent_type, available_date, utilities, requirements,
    status, view_count, create_time, update_time)
VALUES
    (1, 1, '湛江霞山 · 银帆花园精装两居', '霞山区银帆花园，近海滩，家具家电齐全，拎包入住。',
     '银帆花园 2 号', '广东省', '湛江市', '霞山区',
     21.2192681, 110.3923765, 2000.00, 1000.00, 75.00, 2, 1, 1,
     8, 18, '南', '整租', '2026-09-20', '民水民电，天然气', '爱干净、不养宠物',
     1, 128, NOW(), NOW()),
    (2, 2, '湛江赤坎 · 金沙湾海景三居', '赤坎区金沙湾，高层海景，南北通透，近商圈与公园。',
     '金沙湾小区 5 栋', '广东省', '湛江市', '赤坎区',
     21.2732140, 110.3651140, 2500.00, 1250.00, 89.00, 3, 2, 1,
     12, 26, '南北', '整租', '2026-11-01', '民水民电，物业费房东承担', '作息规律',
     1, 96, NOW(), NOW()),
    (3, 1, '湛江霞山 · 国贸旁合租单间', '国贸商圈旁，独立卫生间，包网费，适合实习/通勤。',
     '国贸大厦后面 3 号', '广东省', '湛江市', '霞山区',
     21.2075120, 110.4010230, 1500.00, 750.00, 45.00, 1, 1, 1,
     5, 12, '东', '合租', '2026-10-25', '包网费，水电均摊', '仅限一人，作息规律',
     1, 73, NOW(), NOW());

-- 房源图片：文件在 db/image/，由 compose 的 minio-init 上传到 MinIO 的 nest-rent/demo/
-- 本地默认 MinIO 端口 9012；改端口时同步替换下面的 URL
INSERT IGNORE INTO house_image (id, house_id, url, is_cover, sort_order) VALUES
    (1, 1, 'http://localhost:9012/nest-rent/demo/house-1-cover.jpg', 1, 0),
    (2, 1, 'http://localhost:9012/nest-rent/demo/house-1-detail-1.jpg', 0, 1),
    (3, 1, 'http://localhost:9012/nest-rent/demo/house-1-detail-2.jpg', 0, 2),
    (4, 2, 'http://localhost:9012/nest-rent/demo/house-2-cover.jpg', 1, 0),
    (5, 2, 'http://localhost:9012/nest-rent/demo/house-2-detail-1.jpg', 0, 1),
    (6, 3, 'http://localhost:9012/nest-rent/demo/house-3-cover.jpg', 1, 0);

-- 兼容旧库：把已存在的 demo 图片地址从外部占位图改成 MinIO
UPDATE house_image SET url = CASE id
    WHEN 1 THEN 'http://localhost:9012/nest-rent/demo/house-1-cover.jpg'
    WHEN 2 THEN 'http://localhost:9012/nest-rent/demo/house-1-detail-1.jpg'
    WHEN 3 THEN 'http://localhost:9012/nest-rent/demo/house-1-detail-2.jpg'
    WHEN 4 THEN 'http://localhost:9012/nest-rent/demo/house-2-cover.jpg'
    WHEN 5 THEN 'http://localhost:9012/nest-rent/demo/house-2-detail-1.jpg'
    WHEN 6 THEN 'http://localhost:9012/nest-rent/demo/house-3-cover.jpg'
    ELSE url
END WHERE id IN (1, 2, 3, 4, 5, 6);

-- 房源标签
INSERT IGNORE INTO house_tag (id, house_id, tag_name) VALUES
    (1, 1, '精装修'),
    (2, 1, '近地铁'),
    (3, 1, '拎包入住'),
    (4, 2, '海景房'),
    (5, 2, '有电梯'),
    (6, 2, '南北通透'),
    (7, 3, '合租'),
    (8, 3, '包网费'),
    (9, 3, '近商圈');

-- 住客评价（AI 的 getHouseReviews 工具可直接查到）
INSERT IGNORE INTO review (id, tenant_id, house_id, rating, content, like_count) VALUES
    (1, 1, 1, 5, '房子很干净，房东响应很快，走到海边十分钟，适合两个人住。', 3),
    (2, 1, 2, 4, '海景确实不错，楼下就有便利店，通勤稍微远一点。', 1);
