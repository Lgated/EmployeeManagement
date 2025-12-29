package com.example.empmgmt.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMs;


    public JwtUtil(
            @Value("${jwt.secret:replace-with-256-bit-secret-key-xxxx}") String secret
            // 过期时间 ： 1h
            ,@Value("${jwt.expiration:3600000}")long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMs = expiration;
    }

    /**
     * 生成 JWT TOKEN
     */
    public String generateToken(String username){
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * 从 Token 中解析用户名
     */
    public String parseUsername(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key) // 指定验签密钥
                    .build() // 构建出JwtParser 实例
                    .parseClaimsJws(token) // 把字符串 JWT 解析成 Claims 对象（同时完成签名验证）
                    .getBody() // 取载荷
                    .getSubject(); // 拿用户名
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 Token", e);
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 Token 过期时间（秒）
     */
    public Long getExpirationTime() {
        return ttlMs / 1000;
    }
}
