package com.nest.constant;

/** JWT 双通道认证常量。房东端 Header token，租客端 Header authentication。 */
public final class JwtConstant {

    private JwtConstant() {}

    private static volatile String adminSecretKey;
    private static volatile String userSecretKey;

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

    public static final long ADMIN_TTL = 7200000;
    public static final String ADMIN_TOKEN_NAME = "token";

    public static final long USER_TTL = 7200000;
    public static final String USER_TOKEN_NAME = "authentication";

    public static final String TYPE_LANDLORD = "landlord";
    public static final String TYPE_TENANT = "tenant";
}