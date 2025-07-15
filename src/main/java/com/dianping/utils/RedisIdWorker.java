package com.dianping.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1751046781L;

    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowStamp = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowStamp - BEGIN_TIMESTAMP;

        //2.生成序列号
        //每天/每月同一个自增序列
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2自增
        long count =  stringRedisTemplate.opsForValue().increment("inc" + keyPrefix +":" + date);

        //3.拼接返回
        return timestamp << COUNT_BITS | (int) count;
    }
}
