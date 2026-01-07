package com.example.empmgmt.common.util;

import com.example.empmgmt.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {


    @Getter
    @Value("${jwt.access-ttl-ms:1800000}") // 30min
    private long accessTtlMs;

    @Getter
    @Value("${jwt.refresh-ttl-ms:2592000000}") // 30d
    private long refreshTtlMs;

    @Value("${jwt.secret}")
    private String secret;

    private Key key;


    @PostConstruct // 在依赖注入完成后执行
    public void init() {
        // 使用 HS256 算法生成密钥
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 生成 Access Token
    public String generateAccessToken(User user, String device) {
        return buildToken(user, device, accessTtlMs);
    }

    // 生成 Refresh Token
    public String generateRefreshToken(User user, String device) {
        return buildToken(user, device, refreshTtlMs);
    }


    /**
     * 构建 JWT Token
     * @param user 用户对象
     * @param device 设备/指纹
     * @param ttl 过期时间
     * @return
     */
    private String buildToken(User user, String device, long ttl) {
        String jti = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getUsername());
        claims.put("userId", user.getId());
        claims.put("role", user.getRole());
        claims.put("department", user.getDepartment());
        claims.put("employeeId", user.getEmployeeId());
        claims.put("device", device);
        claims.put("jti", jti); // 唯一标识

        Date now = new Date();
        //setSubject() 设置了 subject（对应 JWT 的 sub 字段）
        //setClaims(claims) 会覆盖所有已设置的 claims，包括 subject
        //由于 claims Map 中没有 "sub" 字段，subject 被清空
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 JWT Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }

    /*    *//**
     * 生成 JWT TOKEN
     *//*
    public String generateToken(User user){

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());  // 添加角色信息
        claims.put("department", user.getDepartment());  // 添加部门信息
        claims.put("employeeId", user.getEmployeeId());  // 添加员工ID

        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims) // 添加用户信息包括角色、部门
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }*/

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

    // Token 有效性验证
    public boolean isExpired(String token) {
        Claims claims = getClaimsFromToken(token);
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }

    // 获取 Token 过期时间
    public LocalDateTime getExpirationDate(String token) {
        Claims claims = getClaimsFromToken(token);
        Date expiration = claims.getExpiration();
        return LocalDateTime.ofInstant(expiration.toInstant(), java.time.ZoneId.systemDefault());
    }
}
