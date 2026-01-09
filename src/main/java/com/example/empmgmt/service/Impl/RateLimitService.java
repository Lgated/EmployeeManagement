package com.example.empmgmt.service.Impl;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 基于 Redis + Lua 的令牌桶限流服务
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public RateLimitService(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        // 加载 Lua 脚本
        this.tokenBucketScript.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        this.tokenBucketScript.setResultType(Long.class);
    }
    /**
     * 尝试获取一个令牌
     *
     * @param keyPrefix 限流 key 前缀，例如 "rate:login:ip:"
     * @param id        标识，例如 IP 或 userId
     * @param capacity  桶容量 -- 瞬间并发
     * @param rate      每秒填充速率（可以是小数）
     * @return true = 允许；false = 限流
     */
    public boolean tryAcquire(String keyPrefix, String id, int capacity, double rate) {
        String tokensKey = keyPrefix + id + ":tokens";
        String tsKey = keyPrefix + id + ":ts";

        long now = System.currentTimeMillis();

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                tokenBucketScript,
                Arrays.asList(tokensKey, tsKey),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(now)
        );
        return result != null && result == 1L;
    }

}
