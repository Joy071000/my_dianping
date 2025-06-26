package com.dianping;

import com.dianping.entity.Shop;
import com.dianping.service.impl.ShopServiceImpl;
import com.dianping.utils.CacheClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static com.dianping.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class DianpingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void testSaveHotKeyWithLogicalTime(){
        Shop shop = shopService.getById(4);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY+4L, shop, 10L, TimeUnit.SECONDS);
    }

}
