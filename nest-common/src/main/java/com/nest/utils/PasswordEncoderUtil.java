package com.nest.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 密码加密工具类，使用 BCrypt 哈希与校验。 */
public final class PasswordEncoderUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordEncoderUtil() {}

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
