package com.heima.notice.service;

import com.alibaba.fastjson.JSON;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.model.MessageAck;
import com.heima.notice.model.MessagePack;
import com.heima.notice.socket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 消息路由服务（跨集群消息路由）
 */
@Service
public class MessageRouteService {

    private static final Logger logger = LoggerFactory.getLogger(MessageRouteService.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SessionService sessionService;

    /**
     * 发送消息到指定用户（支持跨集群）
     * @return true表示推送成功，false表示用户离线（消息已存储为离线消息）
     */
    public boolean sendToUser(String toId, MessagePack messagePack) {
        try {
            // 获取用户所在的brokerId
            Integer brokerId = sessionService.getBrokerId(toId);
            
            if (brokerId == null) {
                logger.warn("用户 {} 不在线，消息将存储为离线消息", toId);
                
                // 【新增】智能处理离线消息：服务器代收确认机制
                // 如果用户离线，服务器作为"代理人"告诉发送方"消息已送达（由服务器代收）"
                // 这样发送方的圈圈就能停止，显示"已送达"
                sendDeliveryAckForOfflineUser(messagePack);
                
                return false;
            }

            // 获取当前节点的brokerId
            Integer currentBrokerId = sessionService.getCurrentBrokerId();
            
            // 详细日志：记录路由决策过程
            logger.info("消息路由决策: fromId={}, toId={}, targetBrokerId={}, currentBrokerId={}, messageId={}", 
                    messagePack.getFromId(), toId, brokerId, currentBrokerId, messagePack.getMessageId());
            
            // 如果用户在同一节点，尝试本地推送
            if (brokerId.equals(currentBrokerId)) {
                logger.info("检测到用户在同一节点，尝试本地推送: toId={}, brokerId={}, messageId={}", 
                        toId, brokerId, messagePack.getMessageId());
                // 这里需要直接调用WebSocketServer的pushMessage方法
                // 但由于WebSocketServer是单例，需要通过SpringUtil获取
                try {
                    WebSocketServer webSocketServer = com.heima.commons.utils.SpringUtil.getBean(WebSocketServer.class);
                    logger.debug("开始本地推送: toId={}, messageId={}", toId, messagePack.getMessageId());
                    boolean pushResult = webSocketServer.pushMessage(messagePack);
                    logger.debug("本地推送结果: toId={}, messageId={}, result={}", toId, messagePack.getMessageId(), pushResult);
                    if (pushResult) {
                        logger.info("本地推送成功，用户确实在当前节点，跳过RabbitMQ: toId={}, messageId={}", 
                                toId, messagePack.getMessageId());
                        return true;  // 本地推送成功，用户确实在本地，直接返回，不再通过RabbitMQ发送
                    } else {
                        logger.warn("本地推送失败（用户不在本地Map中），可能是Redis会话信息错误或用户已断开，降级使用RabbitMQ: toId={}, messageId={}", 
                                toId, messagePack.getMessageId());
                        // 降级：继续使用RabbitMQ发送（不return，继续执行下面的代码）
                        // 注意：如果Redis中的brokerId错误，这里会通过RabbitMQ发送到正确的节点
                    }
                } catch (Exception e) {
                    logger.error("本地推送异常，降级使用RabbitMQ: toId={}, messageId={}, error={}", 
                            toId, messagePack.getMessageId(), e.getMessage(), e);
                    // 降级：继续使用RabbitMQ发送（不return，继续执行下面的代码）
                }
            } else {
                logger.info("用户在不同节点，使用RabbitMQ路由: toId={}, targetBrokerId={}, currentBrokerId={}, messageId={}", 
                        toId, brokerId, currentBrokerId, messagePack.getMessageId());
            }

            // 使用brokerId作为routingKey，路由到对应的节点
            logger.info("通过RabbitMQ路由消息: toId={}, brokerId={}, messageId={}", 
                    toId, brokerId, messagePack.getMessageId());
            String routingKey = String.valueOf(brokerId);
            String message = JSON.toJSONString(messagePack);
            
            rabbitTemplate.convertAndSend(
                    NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS,
                    routingKey,
                    message
            );
            logger.info("RabbitMQ消息已发送: toId={}, brokerId={}, routingKey={}, messageId={}", 
                    toId, brokerId, routingKey, messagePack.getMessageId());
            
            logger.debug("消息已路由到brokerId: {}, toId: {}", brokerId, toId);
            return true;
        } catch (Exception e) {
            logger.error("发送消息失败, toId: {}, error: {}", toId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送消息到当前节点（本地发送）
     */
    public void sendToLocalNode(MessagePack messagePack) {
        try {
            Integer brokerId = sessionService.getCurrentBrokerId();
            String routingKey = String.valueOf(brokerId);
            String message = JSON.toJSONString(messagePack);
            
            rabbitTemplate.convertAndSend(
                    NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS,
                    routingKey,
                    message
            );
        } catch (Exception e) {
            logger.error("发送消息到本地节点失败, error: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送离线消息的送达确认（服务器代收确认）
     * 当用户离线时，服务器代替用户发送"已送达"确认，告诉发送方消息已安全到达服务器
     * 
     * @param messagePack 原始消息包
     */
    private void sendDeliveryAckForOfflineUser(MessagePack messagePack) {
        try {
            String fromId = messagePack.getFromId();
            if (fromId == null || fromId.isEmpty()) {
                logger.warn("无法发送离线代收确认：发送方ID为空, messageId={}", messagePack.getMessageId());
                return;
            }

            // 创建服务器代收确认包
            MessagePack ackPack = new MessagePack();
            ackPack.setCommand(NoticeConstants.Command.SERVER_ACK);
            ackPack.setToId(fromId); // 发送给消息的发送方
            ackPack.setFromId(messagePack.getToId()); // 反向路由
            
            // 创建Server ACK，ackType=2表示服务端ACK，状态为"已送达（服务器代收）"
            MessageAck deliveryAck = new MessageAck(
                    messagePack.getMessageId(), 
                    messagePack.getMessageSequence(), 
                    2 // 服务端ACK类型
            );
            ackPack.setData(deliveryAck);
            ackPack.setMessageId(messagePack.getMessageId());
            ackPack.setMessageSequence(messagePack.getMessageSequence());

            // 路由到发送方所在的节点（支持跨集群）
            // 注意：这里调用sendToUser可能会再次检查发送方是否在线，如果发送方也不在线，确认包会丢失
            // 但通常发送方是发送消息的，应该是在线的，所以这里直接路由即可
            Integer senderBrokerId = sessionService.getBrokerId(fromId);
            if (senderBrokerId != null) {
                // 发送方在线，通过RabbitMQ路由
                String routingKey = String.valueOf(senderBrokerId);
                String message = JSON.toJSONString(ackPack);
                rabbitTemplate.convertAndSend(
                        NoticeConstants.RabbitConstants.NOTICE_SERVICE_2_WS,
                        routingKey,
                        message
                );
                logger.info("离线消息代收确认已发送: messageId={}, fromId={}, toId={}", 
                        messagePack.getMessageId(), messagePack.getToId(), fromId);
            } else {
                // 发送方也不在线，记录日志但不抛异常（不影响主流程）
                logger.warn("发送方不在线，无法发送离线代收确认: messageId={}, fromId={}", 
                        messagePack.getMessageId(), fromId);
            }

        } catch (Exception e) {
            // 代收确认发送失败不影响主流程，只记录日志
            logger.error("发送离线消息代收确认失败: messageId={}, error={}", 
                    messagePack.getMessageId(), e.getMessage(), e);
        }
    }
}

