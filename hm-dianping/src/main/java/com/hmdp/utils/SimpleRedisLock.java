package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    // 锁的名称
    private String name;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";

    // hutool的UUID.randomUUID().toString(true)参数为true可以去掉横线
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    // 类一加载时一起被加载，可以在声明时赋值，或在静态块中赋值
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 在类加载时执行一段代码，用于给静态变量赋值，初始化需要多步操作时，常用静态块给static final变量赋值
    static {
        // 创建脚本对象实例，
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // setLocation设置脚本位置，ClassPathResource是Spring的资源抽象，也就是说位于src/main/resources下的文件
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 明确指定脚本执行后返回的Java类型为Long
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */

    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unlock() {
//        // 获取线程标识，判断是否一致
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        // 获取锁中标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (threadId.equals(id)) {
//            // 释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
