package com.dianping.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.dianping.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //设置空值解决缓存穿透
    public <R, ID> R queryWithPassThrough(
            ID id, String prefix, Class<R> clazz, Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        String key = prefix + id;
        //1.从redis查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.命中直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, clazz);
        }
        //3.未命中，判断是否是设置的“”
        if(json != null){
            return null;
        }
        //4.先查询数据库
        R r = dbFallBack.apply(id);
        //5.判断是否存在
        if(r == null){
            //5.1 不存在，设置空值并返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //5.2存在，写入redis,并返回
        this.set(key, r, time, timeUnit);
        return r;
    }


    //设置逻辑过期时间解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(
            String prefix, ID id, Class<R> clazz, Long time, TimeUnit timeUnit, Function<ID, R> dbFallBack) {
        //1.从redis中查询
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否命中
        if(StrUtil.isBlank(json)){
            //3.未命中直接返回错误
            return null;
        }
        //3.命中，判断是否过期
        //4.先要反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return r;
        }
        //过期，开启新事物重建缓存
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    R newR =  dbFallBack.apply(id);
                    //数据库一定存在,直接开启缓存重建
                    setWithLogicalExpire(key, newR, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });

        }
        //没获取到互斥锁，休眠，并返回旧值
        return r;

    }

    //缓存重建
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    //利用互斥锁解决缓存击穿问题（热点key）
    public <R, ID> R queryWithMutex(
            String prefix, ID id, Class<R> clazz, Long time, TimeUnit timeUnit, Function<ID, R> dbFallBack) {
        //从redis中获取缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //命中则直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, clazz);
        }
        //判断命中的是否是一个空值
        if(json != null){
            //返回错误信息
            return null;
        }
        //需要缓存重建
        //先获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            if(!isLock){
                //获取失败，休眠重试
                Thread.sleep(50);
                return queryWithMutex(prefix, id, clazz, time, timeUnit, dbFallBack);
            }
            //获取成功，查询数据库
            r = dbFallBack.apply(id);
            //判断是否存在
            if(r == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //存在，写入redis
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return r;
    }

        private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete((key));
    }
}
