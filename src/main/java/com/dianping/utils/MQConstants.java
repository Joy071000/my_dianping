package com.dianping.utils;

public class MQConstants {
    // 业务交换机和队列
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ROUTING_KEY_TOPIC = "seckill.#";
    public static final String SECKILL_ROUTING_KEY_QUEUE = "seckill.message";

    // 死信交换机和队列
    public static final String DEAD_LETTER_EXCHANGE = "dead.letterExchange";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead.routingKey";
    public static final String DEAD_LETTER_QUEUE = "dead.queue";

}
