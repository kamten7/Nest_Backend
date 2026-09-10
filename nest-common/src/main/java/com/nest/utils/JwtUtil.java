package com.nest.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;

/** JWT 工具类：生成、解析、校验 Token。SHA-256 哈希确保密钥 ≥ 256 位。 */
public final class JwtUtil {

    private JwtUtil() {}

    /** 密钥字符串 → 256 位 SecretKey。 */
    private static SecretKey toSecretKey(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("JWT 密钥初始化失败", e);
        }
    }

    /** 生成 JWT Token。 */
    public static String createToken(String secret, long ttlMillis, Map<String, Object> claims) {
        SecretKey key = toSecretKey(secret);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** 解析 Token，返回 Claims。签名错误或过期会抛异常。 */
    public static Claims parseToken(String secret, String token) {
        SecretKey key = toSecretKey(secret);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 Token 是否有效。 */
    public static boolean validate(String secret, String token) {
        try {
            parseToken(secret, token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
