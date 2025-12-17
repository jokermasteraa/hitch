package com.heima.notice.mq;

import com.alibaba.fastjson.JSON;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.model.MessagePack;
import com.heima.notice.socket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息接收器（接收路由到本节点的消息）
 */
@Component
public class NoticeMessageReceiver {

    private static final Logger logger = LoggerFactory.getLogger(NoticeMessageReceiver.class);

    @Autowired
    private WebSocketServer webSocketServer;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS + "_${notice.broker-id:1000}", durable = "true"),
                    exchange = @Exchange(value = NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS, type = "topic", durable = "true"),
                    key = "${notice.broker-id:1000}"
            )
    )
    public void receiveMessage(@Payload String messageStr, 
                              @Headers Map<String, Object> headers,
                              com.rabbitmq.client.Channel channel,
                              org.springframework.amqp.core.Message message) {
        try {
            logger.info("RabbitMQ收到消息: {}", messageStr);
            
            MessagePack messagePack = JSON.parseObject(messageStr, MessagePack.class);
            
            // 推送给WebSocket客户端
            if (messagePack.getCommand() == NoticeConstants.Command.PUSH_MESSAGE) {
                logger.info("RabbitMQ接收消息后准备推送: toId={}, messageId={}", 
                        messagePack.getToId(), messagePack.getMessageId());
                boolean result = webSocketServer.pushMessage(messagePack);
                logger.info("RabbitMQ消息推送结果: toId={}, messageId={}, result={}", 
                        messagePack.getToId(), messagePack.getMessageId(), result);
            }
            
            // 手动确认
            Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            logger.error("处理消息失败: {}", e.getMessage(), e);
            try {
                Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
                // 拒绝消息，不重新入队（避免重复处理）
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ex) {
                logger.error("拒绝消息失败: {}", ex.getMessage(), ex);
            }
        }
    }
}

