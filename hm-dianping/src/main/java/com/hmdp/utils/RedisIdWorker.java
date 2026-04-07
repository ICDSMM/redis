package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    // 初始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    // 序列号位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * id生成器
     * @param keyPrefix
     * @return
     */
    public Long nextId(String keyPrefix){
        // 1.生成时间戳 = nowSecond - BEGIN_TIMESTAMP
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号，用到redis自增长increase
        // 2.1获取当前日期，精确到天
        // LocalDate.now().toString()，该方法返回的是"yyyy-MM-dd"
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2自增长，用基本类不用包装类方便做运算
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回，或运算有1为1，全0为0
        return timeStamp << COUNT_BITS | count;
    }

/*
    public static void main(String[] args) {
        // 指定初始时间
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        // 将2022-01-01 00:00:00这个日期时间(无时区信息)解释为 UTC 时区的时刻，然后计算从1970-01-01T00:00:00Z(UTC起点)到该时刻所经过的秒数
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second ="+ second);
    }
*/

}
