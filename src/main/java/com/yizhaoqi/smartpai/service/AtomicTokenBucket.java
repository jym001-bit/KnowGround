package com.yizhaoqi.smartpai.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AtomicTokenBucket {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public AtomicTokenBucket(StringRedisTemplate redis) {
        this.redis = redis;
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/token_bucket.lua"));
        script.setResultType(Long.class);
    }

    /** Returns 0 after taking a token, otherwise milliseconds until the next available token. */
    public long take(String key, double capacity, double tokensPerSecond, boolean background) {
        double bgRate = tokensPerSecond * 0.7d;
        Long wait = redis.execute(script, List.of(key + ":total", key + ":background"),
                Double.toString(capacity), Double.toString(tokensPerSecond / 1000d),
                Double.toString(Math.max(1d, capacity * 0.7d)), Double.toString(bgRate / 1000d),
                background ? "1" : "0");
        if (wait == null) throw new IllegalStateException("Redis 令牌桶未返回结果");
        return wait;
    }
}
