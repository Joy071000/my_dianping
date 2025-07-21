package com.dianping.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianping.dto.Result;
import com.dianping.dto.UserDTO;
import com.dianping.entity.VoucherOrder;
import com.dianping.mapper.VoucherOrderMapper;
import com.dianping.rabbitmq.MQSender;
import com.dianping.service.ISeckillVoucherService;
import com.dianping.service.IVoucherOrderService;
import com.dianping.service.IVoucherService;
import com.dianping.utils.RedisIdWorker;
import com.dianping.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.dianping.utils.MQConstants.SECKILL_EXCHANGE;
import static com.dianping.utils.MQConstants.SECKILL_ROUTING_KEY_QUEUE;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private MQSender mqSender;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

//    //创建异步线程池
//    private static final ExecutorService SECKILL_EXECUTOR = Executors.newSingleThreadExecutor();
//
//    //创建阻塞队列
//    private static final BlockingQueue<VoucherOrder> voucherTasks = new ArrayBlockingQueue<>(1024 * 1024);
//
//    //有可能服务一开启就有用户下单，因此需要初始化
//    @PostConstruct
//    private void init(){
//        SECKILL_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//
//
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run(){
//            while (true) {
//                try{
//                    // 获取队列中的订单信息
//                    VoucherOrder voucherOrder = voucherTasks.take();
//                    // 创建订单
//                    System.out.println("获取订单");
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e){
//                    log.error("预处理订单异常", e);
//
//                }
//            }
//        }
//    }
//
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        RLock lock = redissonClient.getLock("lock:order" + userId);
//        boolean locked = lock.tryLock();
//        if(!locked){
//            log.error("不允许重复下单");
//            return;
//        }
//        try{
//            proxy.createVoucherOrder(voucherOrder);
//        }finally {
//            lock.unlock();
//        }
//    }

//    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        // 1.执行lua脚本
        var result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), user.getId().toString()
        );
        // 2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            // 2.1 不为0，表示没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单！");
        }
        //2.2 为0，表示有购买资格,把下单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(user.getId());
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        voucherTasks.add(voucherOrder);

        //发送到消息队列
        mqSender.sendOrder(SECKILL_EXCHANGE, SECKILL_ROUTING_KEY_QUEUE, voucherOrder);

        return Result.ok(orderId);
    }


    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //查询订单
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if(count > 0){
            log.error("不允许重复下单");
            return;
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if(!success){
            throw new IllegalStateException("库存不足，下单失败！");
        }
        save(voucherOrder);
    }


    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询秒杀优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //判断优惠券秒杀活动开始没？
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //秒杀尚未开始，返回错误信息
//            return Result.fail("秒杀尚未开始！");
//        }
//        //判断秒杀优惠券结束没
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //秒杀已经结束，返回错误信息
//            return Result.fail("秒杀已经结束！");
//        }
//        //判断是否有库存
//        if (seckillVoucher.getStock() < 1) {
//            //库存不足，返回错误信息
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
////        synchronized (userId.toString().intern()) {
////            //获取代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
////        自己加锁
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        //利用redisson获取可重入锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        //获取锁
//        boolean isLock = lock.tryLock();
//        if(!isLock) {
//            return Result.fail("不允许重复下单！");
//        }
//        try {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }



//    @Override
//    @Transactional
//    public Result createVoucherOrder(Long voucherId){
//        Long userId = UserHolder.getUser().getId();
//        //判断该用户是否已经抢购过
//        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if(count > 0){
//            //用户已经购买过
//            return Result.fail("不允许重复下单！");
//        }
//
//
//        //库存减一
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)
//                .update();
//        if(!success){
//            //扣件失败
//            return Result.fail("库存不足！");
//        }
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//
//        //订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        //代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        //返回订单id
//        return Result.ok(orderId);
//    }
}
