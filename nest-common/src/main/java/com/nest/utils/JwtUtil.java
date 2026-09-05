package com.nest.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类：生成、解析、校验 Token。
 *
 * 内部使用 SHA-256 对传入密钥字符串做哈希，确保密钥材料 ≥ 256 位，
 * 满足 JJWT 0.12.x 的 HMAC-SHA 最低安全要求。
 *
 * 典型调用链：登录成功 → createToken 签发令牌 → 前端存起来
 * → 每次请求带上 → 拦截器用 parseToken 解析出 userId。
 */
public final class JwtUtil {

    // 工具类不允许实例化（Java 规范：纯静态方法的类私有构造器）
    private JwtUtil() {}

    /**
     * 把任意长度的密钥字符串转换为 256 位的 SecretKey。
     * SHA-256 输出始终为 32 字节，满足 HS256 要求。
     *
     * @param secret 原始密钥字符串（如 "NESTRENTLANDLORDJWT"）
     * @return HMAC 签名用的 SecretKey 对象
     */
    private static SecretKey toSecretKey(String secret) {
        try {
            // 获取 SHA-256 摘要算法实例（JDK 内置）
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 把密钥字符串转成 UTF-8 字节，再算出 32 字节的哈希
            byte[] keyBytes = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            // JJWT 要求密钥 ≥ 256 位（32 字节），哈希后固定 32 字节满足要求
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            // 密钥初始化失败属于环境问题，直接抛出（不吞异常）
            throw new RuntimeException("JWT 密钥初始化失败", e);
        }
    }

    /**
     * 生成 JWT Token。
     *
     * @param secret   签名密钥（任意长度字符串，内部做 SHA-256 哈希，保证 ≥ 256 位）
     * @param ttlMillis 有效期（毫秒）
     * @param claims   自定义声明（存放 userId、userType 等）
     * @return JWT 字符串
     */
    public static String createToken(String secret, long ttlMillis, Map<String, Object> claims) {
        // 先把密钥字符串转成密钥对象（哈希成 256 位）
        SecretKey key = toSecretKey(secret);
        // 记录当前时间（毫秒），作为签发时间
        long now = System.currentTimeMillis();
        // 链式构建 JWT：塞入声明 → 签发时间 → 过期时间 → 签名 → 输出字符串
        return Jwts.builder()
                .claims(claims)                                   // 自定义声明（userId、userType）
                .issuedAt(new Date(now))                          // 签发时间
                .expiration(new Date(now + ttlMillis))            // 过期时间 = 现在 + 有效期
                .signWith(key)                                    // 用密钥签名（防伪造）
                .compact();                                       // 输出紧凑的 JWT 字符串
    }

    /**
     * 解析 Token，返回 Claims。
     *
     * @param secret 签名密钥（必须和签发时一致，否则验签失败）
     * @param token  前端传来的 JWT 字符串
     * @return 载荷 Claims（可用 get("userId", Long.class) 取值）
     */
    public static Claims parseToken(String secret, String token) {
        // 用同一个密钥对象才能正确验签
        SecretKey key = toSecretKey(secret);
        // 解析并验证签名，拿到 payload（过期/伪造都会抛异常）
        return Jwts.parser()
                .verifyWith(key)       // 用密钥校验签名
                .build()
                .parseSignedClaims(token)  // 解析签名后的 claims
                .getPayload();         // 取出载荷部分
    }

    /**
     * 校验 Token 是否有效。
     *
     * @param secret 签名密钥
     * @param token  JWT 字符串
     * @return true=有效（签名正确且未过期），false=无效
     */
    public static boolean validate(String secret, String token) {
        try {
            parseToken(secret, token);   // 能解析成功说明签名对、没过期
            return true;
        } catch (Exception e) {
            // 解析失败（过期/伪造/格式错）返回 false，不抛异常（调用方可决定怎么处理）
            return false;
        }
    }
}
