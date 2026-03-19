package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        // 尝试获取锁
        boolean isLock = lock.tryLock(1200);
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
}
