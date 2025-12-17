package com.heima.notice.socket;

import com.alibaba.fastjson.JSON;
import com.heima.commons.constant.HtichConstants;
import com.heima.commons.domin.vo.response.ResponseVO;
import com.heima.commons.entity.SessionContext;
import com.heima.commons.enums.BusinessErrors;
import com.heima.commons.helper.RedisSessionHelper;
import com.heima.commons.utils.LocalCollectionUtils;
import com.heima.commons.utils.SpringUtil;
import com.heima.modules.po.NoticePO;
import com.heima.modules.vo.NoticeVO;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.handler.NoticeHandler;
import com.heima.notice.model.MessageAck;
import com.heima.notice.model.MessagePack;
import com.heima.notice.model.NoticeSession;
import com.heima.notice.service.SessionService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务器（支持跨集群、心跳、消息确认）
 */
@Component
@ServerEndpoint(value = "/ws/socket")
public class WebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    // 本地会话存储（用于快速查找）
    private static final Map<String, Session> localSessionMap = new ConcurrentHashMap<>();

    // 注入服务（通过SpringUtil获取，因为WebSocket是单例）
    private static SessionService sessionService;
    private static RedisSessionHelper redisSessionHelper;
    private static com.heima.notice.service.NoticeService noticeService;
    private static Integer brokerId;

    @Value("${notice.broker-id:1000}")
    public void setBrokerId(Integer id) {
        brokerId = id;
    }

    @Autowired
    public void setSessionService(SessionService service) {
        sessionService = service;
    }

    @Autowired
    public void setRedisSessionHelper(RedisSessionHelper helper) {
        redisSessionHelper = helper;
    }
    
    @Autowired
    public void setNoticeService(com.heima.notice.service.NoticeService service) {
        noticeService = service;
    }

    /**
     * 连接建立成功调用
     */
    @OnOpen
    public void onOpen(Session session) {
        String accountId = validToken(session);
        if (StringUtils.isEmpty(accountId)) {
            logger.warn("WebSocket连接失败：Token验证失败");
            try {
                session.close();
            } catch (IOException e) {
                logger.error("关闭连接失败", e);
            }
            return;
        }

        // 保存会话到Redis（支持跨集群）
        // 注意：每次连接都强制更新brokerId，确保节点信息正确
        NoticeSession noticeSession = new NoticeSession(accountId, brokerId);
        sessionService.saveSession(accountId, noticeSession);
        
        // 记录连接信息，用于调试
        logger.info("WebSocket连接成功: accountId={}, brokerId={}, 会话已保存到Redis", accountId, brokerId);

        // 保存到本地Map（快速查找）
        localSessionMap.put(accountId, session);

        // 设置会话属性
        session.getUserProperties().put("accountId", accountId);
        session.getUserProperties().put("lastHeartbeat", System.currentTimeMillis());
        
        // 用户上线后，推送离线消息（参考 l-im 实现）
        pushOfflineMessages(accountId);
    }

    /**
     * 接收客户端消息
     */
    @OnMessage
    public void onMessage(Session session, String message) {
        String accountId = (String) session.getUserProperties().get("accountId");
        if (StringUtils.isEmpty(accountId)) {
            return;
        }

        try {
            // 尝试解析为MessagePack格式
            MessagePack messagePack = null;
            try {
                messagePack = JSON.parseObject(message, MessagePack.class);
            } catch (Exception e) {
                // 如果不是MessagePack格式，可能是旧的NoticeVO格式，兼容处理
                logger.debug("消息不是MessagePack格式，尝试兼容处理: {}", e.getMessage());
                handleLegacyMessage(session, accountId, message);
                return;
            }

            // 处理心跳
            if (messagePack.getCommand() != null && messagePack.getCommand() == NoticeConstants.Command.PING) {
                handlePing(session, accountId);
                return;
            }

            // 处理消息ACK
            if (messagePack.getCommand() != null && messagePack.getCommand() == NoticeConstants.Command.MESSAGE_ACK) {
                handleMessageAck(session, accountId, messagePack);
                return;
            }

            // 处理发送消息
            if (messagePack.getCommand() != null && messagePack.getCommand() == NoticeConstants.Command.SEND_MESSAGE) {
                handleSendMessage(session, accountId, messagePack);
                return;
            }

            // 如果没有command，可能是旧格式，兼容处理
            if (messagePack.getCommand() == null) {
                handleLegacyMessage(session, accountId, message);
                return;
            }

        } catch (Exception e) {
            logger.error("处理消息失败: {}", e.getMessage(), e);
            sendError(session, BusinessErrors.WS_SEND_FAILED);
        }
    }

    /**
     * 处理心跳
     */
    private void handlePing(Session session, String accountId) {
        // 更新心跳时间
        session.getUserProperties().put("lastHeartbeat", System.currentTimeMillis());
        sessionService.updateHeartbeat(accountId);

        // 发送PONG响应
        MessagePack pong = new MessagePack();
        pong.setCommand(NoticeConstants.Command.PONG);
        sendMessage(session, JSON.toJSONString(pong));
    }

    /**
     * 处理消息ACK
     */
    private void handleMessageAck(Session session, String accountId, MessagePack messagePack) {
        try {
            MessageAck ack = JSON.parseObject(JSON.toJSONString(messagePack.getData()), MessageAck.class);
            ack.setAckType(1); // 客户端ACK

            // 发送到消息队列处理
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                SpringUtil.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate != null) {
                MessagePack ackPack = new MessagePack();
                ackPack.setCommand(NoticeConstants.Command.MESSAGE_ACK);
                ackPack.setData(ack);
                String messageStr = JSON.toJSONString(ackPack);
                rabbitTemplate.convertAndSend(
                    NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE,
                    "#",
                    messageStr
                );
            } else {
                // 降级：直接调用handler
                NoticeHandler noticeHandler = SpringUtil.getBean(NoticeHandler.class);
                if (noticeHandler != null) {
                    noticeHandler.handleMessageAck(ack);
                }
            }
        } catch (Exception e) {
            logger.error("处理消息ACK失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理发送消息
     */
    private void handleSendMessage(Session session, String accountId, MessagePack messagePack) {
        messagePack.setFromId(accountId);

        // 发送到消息队列处理（通过RabbitMQ）
        try {
            // 通过SpringUtil获取RabbitTemplate
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate = 
                SpringUtil.getBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
            
            if (rabbitTemplate != null) {
                String messageStr = JSON.toJSONString(messagePack);
                rabbitTemplate.convertAndSend(
                    NoticeConstants.RabbitConstants.WS_2_NOTICE_SERVICE,
                    "#",
                    messageStr
                );
            } else {
                // 降级：直接调用handler
                NoticeHandler noticeHandler = SpringUtil.getBean(NoticeHandler.class);
                if (noticeHandler != null) {
                    boolean success = noticeHandler.handleSendMessage(messagePack);
                    if (!success) {
                        sendError(session, BusinessErrors.WS_SEND_FAILED);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("处理发送消息失败: {}", e.getMessage(), e);
            sendError(session, BusinessErrors.WS_SEND_FAILED);
        }
    }

    /**
     * 关闭连接时调用
     */
    @OnClose
    public void onClose(Session session) {
        String accountId = (String) session.getUserProperties().get("accountId");
        if (StringUtils.isNotEmpty(accountId)) {
            try {
                // 从Redis删除会话（如果Redis可用）
                if (sessionService != null) {
                    sessionService.removeSession(accountId);
                }
            } catch (Exception e) {
                // Redis可能已关闭，忽略错误
                logger.debug("删除会话时Redis不可用: {}", e.getMessage());
            }
            // 从本地Map删除
            localSessionMap.remove(accountId);
            logger.info("WebSocket连接关闭: accountId={}", accountId);
        }
    }

    /**
     * 发生错误时调用
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.error("WebSocket发生错误", throwable);
        String accountId = (String) session.getUserProperties().get("accountId");
        if (StringUtils.isNotEmpty(accountId)) {
            sessionService.removeSession(accountId);
            localSessionMap.remove(accountId);
        }
    }

    /**
     * 推送消息给指定用户（本地推送）
     * @return true表示推送成功，false表示推送失败（用户不在线等）
     */
    public boolean pushMessage(MessagePack messagePack) {
        if (messagePack == null || StringUtils.isEmpty(messagePack.getToId())) {
            logger.warn("推送消息参数无效: messagePack={}", messagePack);
            return false;
        }

        Session session = localSessionMap.get(messagePack.getToId());
        if (session != null && session.isOpen()) {
            try {
                messagePack.setCommand(NoticeConstants.Command.PUSH_MESSAGE);
                String messageStr = JSON.toJSONString(messagePack);
                logger.info("推送消息完整JSON: {}", messageStr);
                sendMessage(session, messageStr);
                logger.debug("消息推送成功（返回true）: toId={}, messageId={}", messagePack.getToId(), messagePack.getMessageId());
                return true;
            } catch (Exception e) {
                logger.error("推送消息失败（返回false）: toId={}, error={}", messagePack.getToId(), e.getMessage(), e);
                return false;
            }
        } else {
            logger.warn("用户不在线或连接已关闭（返回false）: toId={}", messagePack.getToId());
            return false;
        }
    }

    /**
     * 批量推送消息
     */
    public void pushMessage(List<NoticePO> noticePOList) {
        if (noticePOList != null && !noticePOList.isEmpty()) {
            for (NoticePO noticePO : noticePOList) {
                MessagePack messagePack = convertToMessagePack(noticePO);
                pushMessage(messagePack);
            }
        }
    }

    /**
     * 发送消息
     */
    private void sendMessage(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                logger.error("发送消息失败: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(Session session, BusinessErrors error) {
        ResponseVO<?> responseVO = ResponseVO.error(error);
        sendMessage(session, JSON.toJSONString(responseVO));
    }

    /**
     * 验证Token
     */
    private String validToken(Session session) {
        try {
            String token = getSessionToken(session);
            if (StringUtils.isEmpty(token)) {
                return null;
            }

            if (redisSessionHelper == null) {
                redisSessionHelper = SpringUtil.getBean(RedisSessionHelper.class);
            }

            if (redisSessionHelper == null) {
                return null;
            }

            SessionContext context = redisSessionHelper.getSession(token);
            boolean isValid = redisSessionHelper.isValid(context);
            if (isValid) {
                return context.getAccountID();
            }
            return null;
        } catch (Exception e) {
            // Redis可能不可用，记录日志但不抛出异常
            logger.debug("验证Token时Redis不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取Session Token
     */
    private String getSessionToken(Session session) {
        Map<String, List<String>> paramMap = session.getRequestParameterMap();
        List<String> paramList = paramMap.get(HtichConstants.SESSION_TOKEN_KEY);
        return LocalCollectionUtils.getOne(paramList);
    }

    /**
     * 处理旧格式消息（兼容旧代码）
     */
    private void handleLegacyMessage(Session session, String accountId, String message) {
        try {
            // 尝试解析为NoticeVO格式（旧格式）
            NoticeVO noticeVO = JSON.parseObject(message, NoticeVO.class);
            if (noticeVO != null && (noticeVO.getReceiverId() != null || noticeVO.getTripId() != null)) {
                noticeVO.setSenderId(accountId);
                
                // 调用旧的saveNotice方法
                NoticeHandler noticeHandler = SpringUtil.getBean(NoticeHandler.class);
                if (noticeHandler != null) {
                    boolean success = noticeHandler.saveNotice(noticeVO);
                    if (!success) {
                        sendError(session, BusinessErrors.WS_SEND_FAILED);
                    }
                }
                return;
            }
        } catch (Exception e) {
            logger.warn("处理旧格式消息失败: {}", e.getMessage());
        }
        
        // 如果都无法解析，记录错误
        logger.warn("无法识别的消息格式: {}", message);
        sendError(session, BusinessErrors.WS_SEND_FAILED);
    }

    /**
     * 转换NoticePO为MessagePack
     */
    private MessagePack convertToMessagePack(NoticePO noticePO) {
        MessagePack messagePack = new MessagePack();
        messagePack.setFromId(noticePO.getSenderId());
        messagePack.setToId(noticePO.getReceiverId());
        messagePack.setMessageId(noticePO.getMessageId());
        messagePack.setMessageSequence(noticePO.getMessageSequence());
        messagePack.setData(noticePO);
        return messagePack;
    }

    /**
     * 获取所有在线用户ID（本地）
     */
    public List<String> getInLineAccountIds() {
        return new java.util.ArrayList<>(localSessionMap.keySet());
    }

    /**
     * 检查用户是否在线（本地）
     */
    public boolean isUserOnlineLocal(String accountId) {
        Session session = localSessionMap.get(accountId);
        return session != null && session.isOpen();
    }

    /**
     * 推送离线消息（用户上线时调用，参考 l-im 实现）
     */
    private void pushOfflineMessages(String accountId) {
        try {
            if (noticeService == null) {
                noticeService = SpringUtil.getBean(com.heima.notice.service.NoticeService.class);
            }
            
            if (noticeService == null) {
                logger.warn("NoticeService未注入，无法推送离线消息: accountId={}", accountId);
                return;
            }
            
            // 获取离线消息（从序列号0开始，最多100条）
            List<com.heima.modules.po.NoticePO> offlineMessages = noticeService.getOfflineMessages(accountId, 0L, 100);
            
            if (offlineMessages != null && !offlineMessages.isEmpty()) {
                logger.info("用户上线，推送离线消息: accountId={}, count={}", accountId, offlineMessages.size());
                
                for (com.heima.modules.po.NoticePO noticePO : offlineMessages) {
                    MessagePack messagePack = convertToMessagePack(noticePO);
                    boolean pushed = pushMessage(messagePack);
                    
                    if (pushed) {
                        // 推送成功后，删除离线消息（避免重复推送）
                        noticeService.removeOfflineMessage(accountId, noticePO.getMessageId());
                        logger.debug("离线消息推送成功并删除: accountId={}, messageId={}", accountId, noticePO.getMessageId());
                    } else {
                        logger.warn("离线消息推送失败: accountId={}, messageId={}", accountId, noticePO.getMessageId());
                    }
                }
            } else {
                logger.debug("用户无离线消息: accountId={}", accountId);
            }
        } catch (Exception e) {
            logger.error("推送离线消息失败: accountId={}, error={}", accountId, e.getMessage(), e);
        }
    }
}
