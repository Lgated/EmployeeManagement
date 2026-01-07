package com.example.empmgmt.service.Impl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 认证令牌服务，管理刷新令牌和黑名单访问令牌
 */
@Service
public class AuthTokenService {
    private final RedisTemplate<String, String> redisTemplate;

    public AuthTokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 刷新令牌的 Redis
    private String rtKey(Long userId, String device) {
        return "auth:rt:" + userId + ":" + device;
    }


    // 黑名单访问令牌的 Redis
    private String blKey(String jti) {
        return "auth:bl:" + jti;
    }

    // 保存刷新令牌
    public void saveRefreshToken(Long userId, String device, String rt, Duration ttl) {
        redisTemplate.opsForValue().set(rtKey(userId, device), rt, ttl);
    }

    // 获取刷新令牌
    public Optional<String> getRefreshToken(Long userId, String device) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(rtKey(userId, device)));
    }

    // 删除刷新令牌
    public void deleteRefreshToken(Long userId, String device) {
        redisTemplate.delete(rtKey(userId, device));
    }

    // 将访问令牌加入黑名单
    public void blacklistAccessToken(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(blKey(jti), "1", ttl);
    }

    // 检查访问令牌是否在黑名单中
    public boolean isBlacklisted(String jti) {
        return redisTemplate.hasKey(blKey(jti));
    }
}
