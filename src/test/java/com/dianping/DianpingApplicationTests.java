package com.dianping;

import com.dianping.entity.Shop;
import com.dianping.service.impl.ShopServiceImpl;
import com.dianping.utils.CacheClient;
import com.dianping.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.*;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class DianpingApplicationTests {

    @Resource
    private CacheClient cacheClient;


    @Resource
    private ShopServiceImpl shopService;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;

    private RLock lock;

    @BeforeEach
    void setUp() {
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");

        //创建连锁multiLock
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);

    }

    @Test
    void testRedisson() throws InterruptedException {
//        RLock lock = redissonClient.getLock("anyLock");
        boolean isLock = lock.tryLock(1, 1000, TimeUnit.SECONDS);
        if(isLock){
            try {
                System.out.println("执行业务");
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    void testSaveHotKeyWithLogicalTime(){
        Shop shop = shopService.getById(4);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+4L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for(int i = 0; i < 300; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            executorService.submit(runnable);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time:" + (end - begin));
    }

}
