package com.nest.constant;

/**
 * JWT 双通道认证常量。
 *
 * 架构与外卖系统对齐：
 * 
 * - 房东端（管理端）：Header token，密钥 NESTRENTLANDLORDJWT
 * - 租客端（用户端）：Header authentication，密钥 NESTRENTUSERJWT
 * 
 */
public final class JwtConstant {

    private JwtConstant() {}

    // ==================== 房东端 ====================

    /** 房东端 JWT 签名密钥 */
    public static final String ADMIN_SECRET_KEY = "NESTRENTLANDLORDJWT";
    /** 房东端 Token 有效期（毫秒）：2 小时 */
    public static final long ADMIN_TTL = 7200000;
    /** 房东端前端传递的 Header 名称 */
    public static final String ADMIN_TOKEN_NAME = "token";

    // ==================== 租客端 ====================

    /** 租客端 JWT 签名密钥 */
    public static final String USER_SECRET_KEY = "NESTRENTUSERJWT";
    /** 租客端 Token 有效期（毫秒）：2 小时 */
    public static final long USER_TTL = 7200000;
    /** 租客端前端传递的 Header 名称 */
    public static final String USER_TOKEN_NAME = "authentication";

    // ==================== 用户类型 ====================

    /** 房东类型标识 */
    public static final String TYPE_LANDLORD = "landlord";
    /** 租客类型标识 */
    public static final String TYPE_TENANT = "tenant";
}
