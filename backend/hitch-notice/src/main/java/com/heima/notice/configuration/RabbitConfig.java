package com.heima.notice.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    // 定义交换机和队列名称
    public static final String NOTICE_EXCHANGE = "NOTICE_EXCHANGE";
    public static final String NOTICE_QUEUE = "NOTICE_QUEUE";
    public static final String NOTICE_KEY = "notice.push";

    // 1. 定义队列
    @Bean
    public Queue noticeQueue() {
        return new Queue(NOTICE_QUEUE, false); // true表示持久化
    }

    // 2. 定义交换机 (Topic类型比较灵活)
    @Bean
    public TopicExchange noticeExchange() {
        return new TopicExchange(NOTICE_EXCHANGE);
    }

    // 3. 绑定队列到交换机
    @Bean
    public Binding bindingNotice() {
        return BindingBuilder.bind(noticeQueue()).to(noticeExchange()).with(NOTICE_KEY);
    }
}