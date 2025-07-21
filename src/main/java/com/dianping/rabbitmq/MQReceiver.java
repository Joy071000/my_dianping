package com.dianping.rabbitmq;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.dianping.config.RabbitMQTopicConfig;
import com.dianping.entity.VoucherOrder;
import com.dianping.service.IVoucherOrderService;
import com.dianping.service.IVoucherService;
import com.dianping.utils.MQConstants;
import com.dianping.utils.RedisConstants;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.dianping.utils.MQConstants.DEAD_LETTER_QUEUE;
import static com.dianping.utils.RedisConstants.SECKILL_ORDER_KEY;

@Slf4j
@Component
public class MQReceiver {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = MQConstants.SECKILL_QUEUE)
    public void handleOrder(VoucherOrder voucherOrder, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        log.info("收到消息:{}", voucherOrder);

        //获取分布式锁

        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock(SECKILL_ORDER_KEY + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            log.error("获取锁失败，疑似重复下单，消息将被拒绝。userId: {}", userId);
            return; // 直接返回，不再执行后续逻辑
        }
        try{
                voucherOrderService.createVoucherOrder(voucherOrder);
                // 数据库操作成功或者重复下单
                // 秒杀成功，手动ACK
                channel.basicAck(tag, false);
                log.info("优惠券秒杀成功，消息ACK");
        } catch (Exception e){
            // 业务失败
            log.error("业务处理失败，数据库已回滚", e);
            try {
                // 拒绝消息，进入死信队列
                channel.basicReject(tag, false);
                log.warn("订单处理失败，将送往死信队列");
            } catch (IOException ex) {
                log.error("拒绝消息时发生IO一场", ex);
            }
        }
        finally {
            lock.unlock();
        }
    }
    @RabbitListener(queues = DEAD_LETTER_QUEUE)
    public void handlerDeadLetter(VoucherOrder failedOrder, Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag){
        log.error("【死信队列】收到无法处理的订单:{}", failedOrder);
        log.info("失败消息已记录，等待人工处理:{}", message);
//        try {
//            channel.basicAck(tag, false);
//        } catch (IOException e) {
//            log.error("【严重错误】处理死信队列时发生致命异常", e);
//            try{
//                channel.basicReject(tag, false);
//            }
//            catch (IOException ex){
//                log.error("拒绝死信队列时发生IO异常", ex);
//            }
//        }
    }
}
