package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    // 类一加载时一起被加载，可以在声明时赋值，或在静态块中赋值
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 在类加载时执行一段代码，用于给静态变量赋值，初始化需要多步操作时，常用静态块给static final变量赋值
    static {
        // 创建脚本对象实例，
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // setLocation设置脚本位置，ClassPathResource是Spring的资源抽象，也就是说位于src/main/resources下的文件
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 明确指定脚本执行后返回的Java类型为Long
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列，有元素唤醒，无元素阻塞，初始化队列大小
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 当前类初始化完毕后执行
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 线程任务，内部类
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run(){
            // 不断从队列中获取
            while(true){
                try {
                    // 1.获取队列中的订单信息
                    // 没元素会卡住（阻塞），不担心while死循环会造成很大负担
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单处理异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order" + userId);
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        // 判断是否获取锁成功
        if(!isLock){
            // 获取锁失败，返回错误信息，或重试
            log.error("不允许重复下单");
            return;
        }
        // 获取代理对象（事务）
        // try  finally  事务可以生效，因为没有捕获异常。如果catch捕获了异常，需要抛出RuntimeException类型异常，不然事务失效。
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 出不出异常都释放锁
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result secKillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        // 2.判断结果是否为0
        int r = result.intValue();
        if(r != 0) {
            //2.1.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足":"不能重复下单");
        }
        //2.2.为0，有购买资格，把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        // 用户id
        voucherOrder.setUserId(userId);
        // 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 放入阻塞队列
        orderTasks.add(voucherOrder);

        // 获取代理对象，对成员变量初始化
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }

    /*
        @Override
        public Result secKillVoucher(Long voucherId) {
            // 1.查询优惠券
            SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
            // 2.判断秒杀是否开始
            if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
                // 尚未开始
                return Result.fail("秒杀尚未开始");
            }
            // 3.判断秒杀是否结束
            if(voucher.getEndTime().isBefore(LocalDateTime.now())){
                // 已经结束
                return Result.fail("秒杀已结束");
            }
            // 4.判断库存是否充足
            if(voucher.getStock() < 1){
                // 库存不足
                return Result.fail("库存不足");
            }

            // 一人一单
            Long userId = UserHolder.getUser().getId();
    //        if (userId == null) {
    //            // 抛出业务异常或返回错误响应，由全局异常处理器处理
    //            throw new RuntimeException("用户未登录，无法下单");
    //        }
    //        synchronized (userId.toString().intern()) {
    //            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //            return proxy.createVoucherOrder(voucherId);
    //        }
            // 创建对象
            // SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);

            RLock lock = redissonClient.getLock("lock:order" + userId);

            // 尝试获取锁
            boolean isLock = lock.tryLock();
            // 判断是否获取锁成功
            if(!isLock){
                // 获取锁失败，返回错误信息，或重试
                return Result.fail("不允许重复下单");
            }
            // 获取代理对象（事务）
            // try  finally  事务可以生效，因为没有捕获异常。如果catch捕获了异常，需要抛出RuntimeException类型异常，不然事务失效。
            try {
                IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
                return proxy.createVoucherOrder(voucherId);
            } finally {
                // 出不出异常都释放锁
                lock.unlock();
            }
        }
         */

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            // 抛出业务异常或返回错误响应，由全局异常处理器处理
            throw new RuntimeException("用户未登录，无法下单");
        }
        // 5.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.1判断是否存在
        if (count > 0) {
            log.error("该用户已经购买过一次");
        }
        // 不存在，没下过单
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")    // set stock = stock - 1
                // 更新前查到的库存值和获取到的库存值一致，代表之前无人修改库存
                .eq("voucher_id", voucherOrder.getVoucherId())    // where voucher_id = #{voucherId}
                // .eq("stock", voucher.getStock())    // and stock = #{stock}
                .gt("stock", 0)  // and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足");
        }
        // 7.创建订单
        save(voucherOrder);
    }

    /*
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            // 抛出业务异常或返回错误响应，由全局异常处理器处理
            throw new RuntimeException("用户未登录，无法下单");
        }
        // 5.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.1判断是否存在
        if (count > 0) {
            // 存在，则用户已经购买过
            return Result.fail("该用户已经购买过一次");
        }
        // 不存在，没下过单
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")    // set stock = stock - 1
                // 更新前查到的库存值和获取到的库存值一致，代表之前无人修改库存
                .eq("voucher_id", voucherId)    // where voucher_id = #{voucherId}
                // .eq("stock", voucher.getStock())    // and stock = #{stock}
                .gt("stock", 0)  // and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2用户id
        voucherOrder.setUserId(userId);
        // 7.3代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 8.返回订单id
        return Result.ok(orderId);
    }
     */
}
