package com.dianping;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Shop;
import com.dianping.entity.User;
import com.dianping.service.IUserService;
import com.dianping.service.impl.ShopServiceImpl;
import com.dianping.utils.CacheClient;
import com.dianping.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.util.FileCopyUtils;


import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.dianping.utils.RedisConstants.*;
import static com.dianping.utils.RedisConstants.LOGIN_USER_TTL;
import static com.dianping.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private IUserService userService;

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
        for(int i = 1; i <= 14; i++) {
            Shop shop = shopService.getById(i);
            cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + (long) i, shop, 1000L, TimeUnit.SECONDS);
        }
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

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testPublisher(){
        rabbitTemplate.convertAndSend("amq.direct", "my-seckill", "Hello World");
    }

    // 用来将用户token保存到文件的txt文件，并把token保存到redis
    @Test
    void insertUsers(){
        //INSERT INTO `tb_user` VALUES (6, '13456762069', '', 'user_xn5wr3hpsv', '', '2022-02-07 17:54:10', '2022-02-07 17:54:10');
        ClassPathResource phones = new ClassPathResource("phones.txt");
        try(InputStream inputStream = phones.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            String phone;
            while((phone = bufferedReader.readLine()) != null){
                User user = new User();
                user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
                user.setPhone(phone);
                userService.save(user);
            }
        }
        catch (IOException e){
            log.error(e.getMessage());
        }
    }

    // 获取1000个tokens并保存到redis和文件
    @Test
    void saveTokens(){
        String tokenFile = "tokens.txt";
        ClassPathResource phones = new ClassPathResource("phones.txt");
        try(InputStream inputStream = phones.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            BufferedWriter writer = new BufferedWriter(new FileWriter(tokenFile))){
            String phone;
            while((phone = bufferedReader.readLine()) != null){
                User user = userService.query().eq("phone", phone).one();

                //7.保存用户到redis
                //7.1 随机生成token，作为登陆令牌
                String token = UUID.randomUUID().toString(true);
                log.info("token:{}", token);
                //7.2 将user转成HashMap存储
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                //7.3 存储到redis
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                //7.4 设置token的有效期
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                writer.write(token);
                writer.newLine();
            }
        }catch (IOException e){
            log.error(e.getMessage());
        }
    }

}
