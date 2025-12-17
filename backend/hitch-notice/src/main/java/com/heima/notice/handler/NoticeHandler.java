package com.heima.notice.handler;

import com.alibaba.fastjson.JSON;
import com.heima.commons.domin.vo.response.ResponseVO;
import com.heima.commons.enums.BusinessErrors;
import com.heima.commons.exception.BusinessRuntimeException;
import com.heima.commons.utils.CommonsUtils;
import com.heima.modules.po.AccountPO;
import com.heima.modules.po.NoticePO;
import com.heima.modules.po.StrokePO;
import com.heima.modules.vo.NoticeVO;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.model.MessageAck;
import com.heima.notice.model.MessagePack;
import com.heima.notice.service.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 通知处理器（支持有序性、可靠性、幂等性）
 */
@Component
public class NoticeHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(NoticeHandler.class);

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private StrokeAPIService strokeAPIService;

    @Autowired
    private AccountAPIService accountAPIService;

    @Autowired
    private SequenceService sequenceService;

    @Autowired
    private MessageIdempotencyService idempotencyService;

    @Autowired
    private MessageRouteService routeService;


    /**
     * 处理发送消息（支持跨集群）
     */
    public boolean handleSendMessage(MessagePack messagePack) {
        try {
            String fromId = messagePack.getFromId();

            // 检查 fromId
            if (StringUtils.isEmpty(fromId)) {
                logger.warn("消息参数不完整: fromId为空");
                return false;
            }

            // 幂等性检查
            String messageId = messagePack.getMessageId();
            if (StringUtils.isEmpty(messageId)) {
                messageId = UUID.randomUUID().toString();
                messagePack.setMessageId(messageId);
            }

            if (idempotencyService.isMessageProcessed(messageId)) {
                logger.debug("消息已处理，跳过: messageId={}", messageId);
                // 消息已处理，直接返回成功，不重复推送（避免重复发送）
                // 如果需要重新推送离线消息，应该通过其他机制（如用户上线时查询离线消息）
                return true;
            }

            // 初始化通知数据（会尝试从 data 中提取 toId，例如通过 tripId）
            NoticeVO noticeVO = initNoticeVO(messagePack);
            if (noticeVO == null) {
                logger.warn("无法确定接收者: fromId={}, toId={}", fromId, messagePack.getToId());
                return false;
            }

            // 从 noticeVO 获取实际的接收者ID，并更新 messagePack.toId
            String toId = noticeVO.getReceiverId();
            if (StringUtils.isEmpty(toId)) {
                logger.warn("消息参数不完整: 无法确定接收者ID");
                return false;
            }
            
            // 更新 messagePack 的 toId（如果之前为空）
            if (StringUtils.isEmpty(messagePack.getToId())) {
                messagePack.setToId(toId);
            }

            // 生成序列号（保证有序性）
            Long sequence = sequenceService.getMessageSequence(fromId, toId);
            messagePack.setMessageSequence(sequence);

            NoticePO noticePO = CommonsUtils.toPO(noticeVO);
            noticePO.setMessageId(messageId);
            noticePO.setMessageSequence(sequence);
            
            // 确保message字段被正确设置
            if (StringUtils.isEmpty(noticePO.getMessage())) {
                logger.warn("NoticePO的message字段为空，尝试从noticeVO获取: noticeVO.message={}", noticeVO.getMessage());
                noticePO.setMessage(noticeVO.getMessage());
            }
            
            logger.info("准备推送消息: messageId={}, fromId={}, toId={}, message={}", 
                    messageId, noticePO.getSenderId(), noticePO.getReceiverId(), noticePO.getMessage());

            // 1. 先存库（持久化是第一位的）
            noticeService.addNotice(noticePO);

            // 2. 存储离线消息到 Redis ZSet（参考 l-im：不管用户是否在线，总是存储）
            noticeService.storeOfflineMessage(noticePO);

            // 3. 标记消息已处理（幂等性）
            idempotencyService.markMessageProcessed(messageId, noticePO);

            // 4. 【新增】Server ACK：服务器收到消息并存入MongoDB后，立刻给发送方回一个确认包
            // 这样发送方就知道消息已经安全到达服务器了
            sendServerReceivedAck(noticePO);

            // 5. 然后再尝试推给目标用户
            pushMessageToUser(noticePO);

            logger.info("消息处理成功: messageId={}, fromId={}, toId={}, sequence={}", 
                    messageId, fromId, toId, sequence);
            return true;

        } catch (Exception e) {
            logger.error("处理发送消息失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 处理消息ACK（双ACK机制）
     */
    public void handleMessageAck(MessageAck ack) {
        try {
            String messageId = ack.getMessageId();
            Integer ackType = ack.getAckType();

            if (ackType == 1) {
                // 客户端ACK：客户端已收到消息
                logger.debug("收到客户端ACK: messageId={}", messageId);
                
                // 发送服务端ACK给发送方
                sendServerAck(messageId, ack.getMessageSequence());
                
            } else if (ackType == 2) {
                // 服务端ACK：服务端已确认客户端收到消息
                logger.debug("收到服务端ACK: messageId={}", messageId);
                // 可以在这里更新消息状态为已确认
            }

        } catch (Exception e) {
            logger.error("处理消息ACK失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送服务器收到确认（Server Received ACK）
     * 在消息存入MongoDB后立即发送，告诉发送方消息已安全到达服务器
     */
    private void sendServerReceivedAck(NoticePO noticePO) {
        try {
            MessagePack ackPack = new MessagePack();
            ackPack.setCommand(NoticeConstants.Command.SERVER_ACK);
            ackPack.setToId(noticePO.getSenderId()); // 发送给消息的发送方
            ackPack.setFromId(noticePO.getReceiverId()); // 反向路由
            
            // 创建Server ACK，ackType=2表示服务端ACK
            MessageAck serverAck = new MessageAck(
                    noticePO.getMessageId(), 
                    noticePO.getMessageSequence(), 
                    2 // 服务端ACK类型
            );
            ackPack.setData(serverAck);
            ackPack.setMessageId(noticePO.getMessageId());
            ackPack.setMessageSequence(noticePO.getMessageSequence());

            // 路由到发送方所在的节点（支持跨集群）
            routeService.sendToUser(noticePO.getSenderId(), ackPack);
            
            logger.info("Server ACK已发送: messageId={}, fromId={}, toId={}", 
                    noticePO.getMessageId(), noticePO.getReceiverId(), noticePO.getSenderId());

        } catch (Exception e) {
            // Server ACK发送失败不影响主流程，只记录日志
            logger.error("发送Server ACK失败: messageId={}, error={}", 
                    noticePO.getMessageId(), e.getMessage(), e);
        }
    }

    /**
     * 发送服务端ACK给发送方（客户端ACK后的确认）
     * 当客户端收到消息并发送ACK后，服务端再给发送方一个确认
     */
    private void sendServerAck(String messageId, Long messageSequence) {
        try {
            NoticePO noticePO = idempotencyService.getProcessedMessage(messageId);
            if (noticePO == null) {
                return;
            }

            MessagePack ackPack = new MessagePack();
            ackPack.setCommand(NoticeConstants.Command.SERVER_ACK);
            ackPack.setToId(noticePO.getSenderId());
            ackPack.setFromId(noticePO.getReceiverId());

            MessageAck serverAck = new MessageAck(messageId, messageSequence, 2);
            ackPack.setData(serverAck);

            // 路由到发送方所在的节点
            routeService.sendToUser(noticePO.getSenderId(), ackPack);

        } catch (Exception e) {
            logger.error("发送服务端ACK失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 推送消息给用户（支持跨集群）
     */
    private void pushMessageToUser(NoticePO noticePO) {
        try {
            // 确保message字段有值
            if (StringUtils.isEmpty(noticePO.getMessage())) {
                logger.warn("推送消息时NoticePO的message字段为空: messageId={}, fromId={}, toId={}", 
                        noticePO.getMessageId(), noticePO.getSenderId(), noticePO.getReceiverId());
            } else {
                logger.info("推送消息: messageId={}, message={}", noticePO.getMessageId(), noticePO.getMessage());
            }
            
            // 记录NoticePO的完整信息用于调试
            logger.info("NoticePO对象信息: messageId={}, senderId={}, receiverId={}, message={}, tripId={}", 
                    noticePO.getMessageId(), noticePO.getSenderId(), noticePO.getReceiverId(), 
                    noticePO.getMessage(), noticePO.getTripId());
            
            MessagePack messagePack = new MessagePack();
            messagePack.setCommand(NoticeConstants.Command.PUSH_MESSAGE);
            messagePack.setFromId(noticePO.getSenderId());
            messagePack.setToId(noticePO.getReceiverId());
            messagePack.setMessageId(noticePO.getMessageId());
            messagePack.setMessageSequence(noticePO.getMessageSequence());
            messagePack.setData(noticePO);
            
            // 记录data字段的JSON序列化结果
            try {
                String dataJson = JSON.toJSONString(noticePO);
                logger.info("NoticePO序列化为JSON: {}", dataJson);
            } catch (Exception e) {
                logger.error("序列化NoticePO失败", e);
            }

            // 路由到接收者所在的节点
            boolean sent = routeService.sendToUser(noticePO.getReceiverId(), messagePack);
            
            if (!sent) {
                logger.warn("用户不在线，消息已存储: receiverId={}", noticePO.getReceiverId());
            }

        } catch (Exception e) {
            logger.error("推送消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 初始化通知数据
     */
    private NoticeVO initNoticeVO(MessagePack messagePack) {
        NoticeVO noticeVO = new NoticeVO();
        noticeVO.setSenderId(messagePack.getFromId());
        noticeVO.setReceiverId(messagePack.getToId());

        // 统一处理data对象，同时提取tripId和message
            Object data = messagePack.getData();
        String tripId = null;
        String message = null;
        
            if (data != null) {
            try {
                // 尝试解析为NoticeVO
                NoticeVO dataVO = JSON.parseObject(JSON.toJSONString(data), NoticeVO.class);
                if (dataVO != null) {
                    tripId = dataVO.getTripId();
                    message = dataVO.getMessage();
                }
            } catch (Exception e) {
                logger.debug("解析data为NoticeVO失败，尝试从Map获取: error={}", e.getMessage());
                // 如果解析失败，尝试从Map中获取
                if (data instanceof java.util.Map) {
                    java.util.Map<?, ?> dataMap = (java.util.Map<?, ?>) data;
                    Object tripIdObj = dataMap.get("tripId");
                    Object msgObj = dataMap.get("message");
                    if (tripIdObj != null) {
                        tripId = tripIdObj.toString();
                    }
                    if (msgObj != null) {
                        message = msgObj.toString();
                    }
                    logger.info("从Map中获取数据: tripId={}, message={}", tripId, message);
                }
            }
        }

        // 如果没有指定接收者，从行程中获取
        if (StringUtils.isEmpty(noticeVO.getReceiverId()) && StringUtils.isNotEmpty(tripId)) {
            logger.info("从tripId查找接收者: tripId={}", tripId);
            StrokePO tripPO = strokeAPIService.selectByID(tripId);
                    if (tripPO != null) {
                String publisherId = tripPO.getPublisherId();
                logger.info("找到行程发布者: publisherId={}", publisherId);
                noticeVO.setReceiverId(publisherId);
                noticeVO.setTripId(tripId);
            } else {
                logger.warn("未找到行程信息: tripId={}", tripId);
            }
        }

        if (StringUtils.isEmpty(noticeVO.getReceiverId())) {
            logger.warn("无法确定接收者");
            return null;
        }

        // 获取用户别名
        noticeVO.setSenderUseralias(getUserAlias(noticeVO.getSenderId()));
        noticeVO.setReceiverUseralias(getUserAlias(noticeVO.getReceiverId()));

        // 设置消息内容
        if (StringUtils.isNotEmpty(message)) {
            noticeVO.setMessage(message);
            logger.info("设置消息内容成功: message={}", message);
        } else {
            logger.warn("消息内容为空: fromId={}, toId={}", noticeVO.getSenderId(), noticeVO.getReceiverId());
        }

        return noticeVO;
    }

    /**
     * 查询消息列表
     */
    public ResponseVO<NoticeVO> list(NoticeVO noticeVO) {
        initNotifyData(noticeVO);
        NoticePO noticePO = CommonsUtils.toPO(noticeVO);
        boolean check = checkParameter(noticePO);
        if (!check) {
            throw new BusinessRuntimeException(BusinessErrors.PARAM_CANNOT_EMPTY);
        }
        java.util.List<NoticePO> noticePOList = noticeService.queryList(noticeVO);
        @SuppressWarnings("unchecked")
        ResponseVO<NoticeVO> result = ResponseVO.success(noticePOList);
        return result;
    }

    /**
     * 发送通知（兼容旧接口）
     */
    public boolean saveNotice(NoticeVO noticeVO) {
        boolean sendOK = false;
        initNotifyData(noticeVO);
        NoticePO noticePO = CommonsUtils.toPO(noticeVO);
        boolean check = checkParameter(noticePO);
        if (check) {
            // 生成消息ID和序列号
            String messageId = UUID.randomUUID().toString();
            Long sequence = sequenceService.getMessageSequence(noticePO.getSenderId(), noticePO.getReceiverId());
            noticePO.setMessageId(messageId);
            noticePO.setMessageSequence(sequence);

            noticeService.addNotice(noticePO);
            idempotencyService.markMessageProcessed(messageId, noticePO);
            
            // 推送消息
            pushMessageToUser(noticePO);
            sendOK = true;
        }
        return sendOK;
    }

    /**
     * 初始化数据
     */
    private void initNotifyData(NoticeVO noticeVO) {
        noticeVO.setSenderUseralias(getUserAlias(noticeVO.getSenderId()));
        String receiverId = noticeVO.getReceiverId();
        if (StringUtils.isEmpty(receiverId)) {
            StrokePO tripPO = strokeAPIService.selectByID(noticeVO.getTripId());
            if (null == tripPO) {
                return;
            }
            receiverId = tripPO.getPublisherId();
            noticeVO.setReceiverId(receiverId);
        }
        noticeVO.setReceiverUseralias(getUserAlias(receiverId));
    }

    public boolean checkParameter(NoticePO noticePO) {
        if (null == noticePO) {
            return false;
        }
        if (StringUtils.isEmpty(noticePO.getSenderId())) {
            return false;
        }
        if (StringUtils.isEmpty(noticePO.getReceiverId())) {
            return false;
        }
        return true;
    }

    /**
     * 获取当前用户姓名
     */
    private String getUserAlias(String accountId) {
        if (StringUtils.isEmpty(accountId)) {
            return null;
        }
        AccountPO accountPO = accountAPIService.getAccountByID(accountId);
        if (null == accountPO) {
            return null;
        }
        return StringUtils.isEmpty(accountPO.getUseralias()) ? accountPO.getUsername() : accountPO.getUseralias();
    }
}
