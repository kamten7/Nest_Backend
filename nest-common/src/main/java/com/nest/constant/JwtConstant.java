package com.nest.constant;

/** JWT 双通道认证常量。房东端 Header token，租客端 Header authentication。 */
public final class JwtConstant {

    private JwtConstant() {}

    // ==================== 签名密钥（启动时注入，禁止硬编码） ====================

    private static volatile String adminSecretKey;
    private static volatile String userSecretKey;

    /** 由 JwtSecretInitializer 在启动阶段调用一次。 */
    public static void initSecrets(String admin, String user) {
        adminSecretKey = admin;
        userSecretKey = user;
    }

    public static String adminSecretKey() {
        return require(adminSecretKey, "nest.jwt.admin-secret-key / NEST_JWT_ADMIN_SECRET");
    }

    public static String userSecretKey() {
        return require(userSecretKey, "nest.jwt.user-secret-key / NEST_JWT_USER_SECRET");
    }

    private static String require(String value, String hint) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("JWT 密钥未初始化：" + hint);
        }
        return value;
    }

    // ==================== 房东端 ====================

    /** 房东端 Token 有效期（毫秒）：2 小时 */
    public static final long ADMIN_TTL = 7200000;
    /** 房东端前端传递的 Header 名称 */
    public static final String ADMIN_TOKEN_NAME = "token";

    // ==================== 租客端 ====================

    /** 租客端 Token 有效期（毫秒）：2 小时 */
    public static final long USER_TTL = 7200000;
    /** 租客端前端传递的 Header 名称 */
    public static final String USER_TOKEN_NAME = "authentication";

    // ==================== 用户类型 ====================

    public static final String TYPE_LANDLORD = "landlord";
    public static final String TYPE_TENANT = "tenant";
}