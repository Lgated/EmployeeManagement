package com.example.empmgmt;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希生成器
 * 用于生成正确的BCrypt密码哈希
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 生成超级管理员密码哈希
        String adminPassword = "admin123";
        String adminHash = passwordEncoder.encode(adminPassword);
        System.out.println("超级管理员密码: " + adminPassword);
        System.out.println("BCrypt哈希: " + adminHash);
        System.out.println();

        // 生成默认密码哈希
        String defaultPassword = "123456";
        String defaultHash = passwordEncoder.encode(defaultPassword);
        System.out.println("默认密码: " + defaultPassword);
        System.out.println("BCrypt哈希: " + defaultHash);
        System.out.println();

        // 验证测试
        System.out.println("验证超级管理员密码: " + 
                passwordEncoder.matches(adminPassword, adminHash));
        System.out.println("验证默认密码: " + 
                passwordEncoder.matches(defaultPassword, defaultHash));
    }
}
