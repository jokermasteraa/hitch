package com.heima.notice.mq;

import com.alibaba.fastjson.JSON;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.handler.NoticeHandler;
import com.heima.notice.model.MessageAck;
import com.heima.notice.model.MessagePack;
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
 * WebSocket消息接收器（接收来自WebSocket的消息）
 */
@Component
public class WebSocketMessageReceiver {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketMessageReceiver.class);

    @Autowired
    private NoticeHandler noticeHandler;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE, durable = "true"),
                    exchange = @Exchange(value = NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE, type = "topic", durable = "true"),
                    key = "#"
            )
    )
    public void receiveWebSocketMessage(@Payload String messageStr,
                                        @Headers Map<String, Object> headers,
                                        com.rabbitmq.client.Channel channel,
                                        org.springframework.amqp.core.Message message) {
        Long deliveryTag = null;
        try {
            logger.debug("收到WebSocket消息: {}", messageStr);
            
            // 获取deliveryTag（必须在try块开始就获取，确保异常时也能使用）
            deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
            if (deliveryTag == null) {
                logger.error("无法获取deliveryTag，跳过消息处理");
                return;
            }
            
            MessagePack messagePack = JSON.parseObject(messageStr, MessagePack.class);
            boolean success = false;
            
            // 处理不同类型的消息
            if (messagePack.getCommand() == NoticeConstants.Command.SEND_MESSAGE) {
                // 处理发送消息（核心业务逻辑）
                // 只有业务处理成功（返回true），才认为消息处理完成
                success = noticeHandler.handleSendMessage(messagePack);
            } else if (messagePack.getCommand() == NoticeConstants.Command.MESSAGE_ACK) {
                // 处理消息ACK
                MessageAck ack = JSON.parseObject(JSON.toJSONString(messagePack.getData()), MessageAck.class);
                noticeHandler.handleMessageAck(ack);
                success = true; // ACK处理通常不会失败
            } else {
                logger.warn("未知的消息命令: {}", messagePack.getCommand());
                success = true; // 未知命令不重试
            }
            
            // 【关键改造】"不见兔子不撒鹰"：只有业务处理成功，才告诉MQ删除消息
            if (success) {
                channel.basicAck(deliveryTag, false);
                logger.debug("消息处理成功，已ACK: deliveryTag={}, messageId={}", 
                        deliveryTag, messagePack.getMessageId());
            } else {
                // 业务处理失败，明确告诉MQ处理失败，需要重试
                channel.basicNack(deliveryTag, false, true);
                logger.warn("消息处理失败，已NACK（将重试）: deliveryTag={}, messageId={}", 
                        deliveryTag, messagePack.getMessageId());
            }
            
        } catch (Exception e) {
            logger.error("处理WebSocket消息异常: {}", e.getMessage(), e);
            try {
                if (deliveryTag != null) {
                    // 【关键改造】异常情况下，明确告诉MQ处理失败，需要重试
                    // requeue=true 表示把消息放回队列头部，等会再试一次
                    // 这样就保证了消息绝对不会丢
                    channel.basicNack(deliveryTag, false, true);
                    logger.warn("消息处理异常，已NACK（将重试）: deliveryTag={}", deliveryTag);
                }
            } catch (Exception ex) {
                logger.error("拒绝消息失败: {}", ex.getMessage(), ex);
                // 如果NACK也失败，消息会一直处于Unacked状态，需要人工介入
            }
        }
    }
}

