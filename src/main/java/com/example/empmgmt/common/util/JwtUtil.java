package com.example.empmgmt.common.util;

import com.example.empmgmt.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
    public String generateToken(User user){

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());  // 添加角色信息
        claims.put("department", user.getDepartment());  // 添加部门信息
        claims.put("employeeId", user.getEmployeeId());  // 添加员工ID

        Date now = new Date();
        //setSubject() 设置了 subject（对应 JWT 的 sub 字段）
        //setClaims(claims) 会覆盖所有已设置的 claims，包括 subject
        //由于 claims Map 中没有 "sub" 字段，subject 被清空
        return Jwts.builder()
                .setClaims(claims) // 添加用户信息包括角色、部门
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 从Token中获取部门
     */
    public String getDepartmentFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("department", String.class);
    }

    /**
     * 从 Token 中解析用户ID
     */
    public Long parseUserId(String token){
        Claims claimsFromToken = getClaimsFromToken(token);
        return claimsFromToken.get("userId", Long.class);
    }

    /**
     * 从 Token 中解析 Claims
     */
    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key) // 指定验签密钥
                    .build() // 构建出JwtParser 实例
                    .parseClaimsJws(token) // 把字符串 JWT 解析成 Claims 对象（同时完成签名验证）
                    .getBody();// 取载荷
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的Token", e);
        }
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
     * 获取 Token 过期时间（秒）
     */
    public Long getExpirationTime() {
        return ttlMs / 1000;
    }
}
