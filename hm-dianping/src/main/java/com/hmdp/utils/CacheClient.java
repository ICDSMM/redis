package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 线程池10个线程
    private static final ExecutorService CHACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private Boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 写入缓存，设计过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 写入缓存，逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key,Object value,Long time, TimeUnit unit){
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(unit.toSeconds(time));
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(expireTime);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    // Function<ID,R> dbFalBack，前参数类型，后返回值类型
    /**
     * 缓存穿透的代码封装
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String dataJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在，isNotBlank只有"abc"才是true
        if(StrUtil.isNotBlank(dataJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(dataJson, type);
        }
        // 判断命中的是否是空置，即shopJson为""，意味着之前查过数据库，redis的缓存为空
        if(dataJson != null){
            // 返回错误信息
            return null;
        }
        // 4.不存在，根据id查询数据库
        R data = dbFallBack.apply(id);
        // 5.不存在，返回错误
        if(data == null){
            // 将空值写入redis
            this.set(key, data, time, unit);
            return null;
        }
        // 6.存在写入redis
        this.set(key,data,time,unit);
        // 7.返回
        return data;
    }


    /**
     * 逻辑过期解决缓存击穿
     * @param cacheKeyPrefix
     * @param lockKeyPrefix
     * @param id
     * @param type
     * @param dbFallBack
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID>R queryWithLogicalExpire(String cacheKeyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){
        String key = cacheKeyPrefix + id;
        String lockKey = lockKeyPrefix + id;
        // 1.从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.是否命中
        if(StrUtil.isBlank(json)) {
            // 3.未命中，直接返回空
            return null;
        }
        // 4.命中，判断缓存是否过期，先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R data = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5. 未过期，返回信息
            return data;
        }
        // 6. 已过期,需要缓存重建
        // 尝试获取互斥锁
        Boolean isLock = tryLock(lockKey);
        // 7. 判断是否获取锁
        if(isLock){
            // 8. 获取锁，开启独立线程，并返回独立信息
            CHACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 重建缓存
                    // 先查数据库
                    R newData = dbFallBack.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key,newData,time,unit);
                }catch (Exception e){
                    throw new RuntimeException();
                }finally {
                    unlock(lockKey);
                }
            });
        }
        // 9. 未获取锁，返回过期的信息
        // 无论是否获取锁都需要返回信息，所以将返回信息放到判断外
        return data;
    }



}
