package com.dianping.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.dianping.utils.MQConstants.*;

@Configuration
public class RabbitMQTopicConfig {

    // 死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
    // 死信队列
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }
    // 死信交换机和死信队列绑定
    @Bean
    public Binding deadLetterBinding(){
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_ROUTING_KEY);
    }


    // 创建秒杀队列并绑定死信交换机
    @Bean
    public Queue seckillQueue(){
        return QueueBuilder
                .durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY).build();
    }
    //创建秒杀业务交换机
    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange(SECKILL_EXCHANGE, true, false);
    }
    @Bean
    public Binding binding(){
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY_TOPIC);
    }
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
