package com.nest.utils;

//引入BCryptPasswordEncoder类
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类：使用 BCrypt 算法对密码进行哈希与校验
 */
public final class PasswordEncoderUtil {

    // 全局共享一个编码器实例（线程安全、可复用）
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // 工具类不允许实例化（Java 规范：纯静态方法的类私有构造器）
    private PasswordEncoderUtil() {}

    /**
     * 对明文密码进行 BCrypt 加密
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与 BCrypt 密文是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
