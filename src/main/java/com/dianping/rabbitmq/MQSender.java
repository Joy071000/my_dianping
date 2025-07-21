package com.dianping.rabbitmq;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MQSender {
    @Resource
    private RabbitTemplate rabbitTemplate;

    public <T> void sendOrder(String exchange, String routingKey, T message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("消息发送成功！:{}", message);
    }
}
