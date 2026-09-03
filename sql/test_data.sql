-- ============================================================
-- 测试数据（通过 DataGrip "Run SQL Script" 导入，不乱码）
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO landlord (name, phone, password) VALUES
('张房东', '13800000001', 'e10adc3949ba59abbe56e057f20f883e'),
('李房东', '13800000002', 'e10adc3949ba59abbe56e057f20f883e');

INSERT INTO tenant (openid, nickname, phone) VALUES
('test_openid_001', '测试用户小王', '13900000001'),
('test_openid_002', '测试用户小陈', '13900000002');
