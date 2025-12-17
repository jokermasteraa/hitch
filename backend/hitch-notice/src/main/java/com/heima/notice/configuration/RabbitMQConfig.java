package com.heima.notice.configuration;

import com.heima.notice.constant.NoticeConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 */
@Configuration
public class RabbitMQConfig {

    @Value("${notice.broker-id:1000}")
    private Integer brokerId;

    @Autowired
    private ConnectionFactory connectionFactory;

    /**
     * 业务服务到WebSocket网关的Exchange
     */
    @Bean
    public TopicExchange noticeService2WsExchange() {
        return new TopicExchange(NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS, true, false);
    }

    /**
     * WebSocket网关到业务服务的Exchange
     */
    @Bean
    public TopicExchange ws2NoticeServiceExchange() {
        return new TopicExchange(NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE, true, false);
    }

    /**
     * 当前节点的队列（用于接收路由到本节点的消息）
     */
    @Bean
    public Queue noticeServiceQueue() {
        return QueueBuilder.durable(NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS + "_" + brokerId).build();
    }

    /**
     * 绑定当前节点队列到Exchange
     */
    @Bean
    public Binding noticeServiceBinding() {
        return BindingBuilder
                .bind(noticeServiceQueue())
                .to(noticeService2WsExchange())
                .with(String.valueOf(brokerId));
    }

    /**
     * WebSocket发送到业务服务的队列
     */
    @Bean
    public Queue ws2NoticeServiceQueue() {
        return QueueBuilder.durable(NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE).build();
    }

    /**
     * 绑定WebSocket到业务服务的队列
     */
    @Bean
    public Binding ws2NoticeServiceBinding() {
        return BindingBuilder
                .bind(ws2NoticeServiceQueue())
                .to(ws2NoticeServiceExchange())
                .with("#");
    }

    /**
     * 消息存储队列
     */
    @Bean
    public Queue storeNoticeMessageQueue() {
        return QueueBuilder.durable(NoticeConstants.RabbitConstants.STORE_NOTICE_MESSAGE).build();
    }

    /**
     * 消息存储Exchange
     */
    @Bean
    public TopicExchange storeNoticeMessageExchange() {
        return new TopicExchange(NoticeConstants.RabbitConstants.STORE_NOTICE_MESSAGE, true, false);
    }

    /**
     * 绑定消息存储队列
     */
    @Bean
    public Binding storeNoticeMessageBinding() {
        return BindingBuilder
                .bind(storeNoticeMessageQueue())
                .to(storeNoticeMessageExchange())
                .with("#");
    }

    /**
     * 配置消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        // 开启确认模式
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("消息发送失败: " + cause);
            }
        });
        return template;
    }

    /**
     * 配置监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }
}

