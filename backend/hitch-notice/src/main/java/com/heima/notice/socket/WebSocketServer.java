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
import com.heima.notice.configuration.RabbitConfig;
import com.heima.notice.handler.NoticeHandler;
import com.heima.notice.service.NoticeService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint(value = "/ws/socket")
public class  WebSocketServer {


    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    private RabbitTemplate getRabbitTemplate() {
        return SpringUtil.getBean(RabbitTemplate.class);
    }

    //concurrent包的线程安全Map，用来存放每个客户端对应的WebSocketServer对象。
    private static Map<String, Session> sessionPools = new ConcurrentHashMap<>();

    /**
     * 获取所有在线用户列表
     *
     * @return
     */
    public List<String> getInLineAccountIds() {
        List<String> list = new ArrayList();
        list.addAll(sessionPools.keySet());
        return list;
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        String accountId = validToken(session);
        if (StringUtils.isEmpty(accountId)) {
            return;
        }

        //消息里面有接收人id，行程id，消息
        NoticeVO noticeVO = JSON.parseObject(message, NoticeVO.class);
        noticeVO.setSenderId(accountId);
        noticeVO.setReceiverId(noticeVO.getReceiverId());

        noticeVO.setSendTime(System.currentTimeMillis());

        NoticeHandler noticeHandler = SpringUtil.getBean(NoticeHandler.class);
        RabbitTemplate rabbitTemplate = SpringUtil.getBean(RabbitTemplate.class);

        if (noticeHandler != null) {
            // 1. 先存库 (read=false)
            boolean saveOK = noticeHandler.saveNotice(noticeVO);

            if (!saveOK) {
                ResponseVO responseVO = ResponseVO.error(BusinessErrors.WS_SEND_FAILED);
                sendMessage(session, JSON.toJSONString(responseVO));
            } else {
                // 2. 存库成功，发给 MQ
                if (rabbitTemplate != null) {
                    try {
                        String msgJson = JSON.toJSONString(noticeVO);
                        rabbitTemplate.convertAndSend(RabbitConfig.NOTICE_EXCHANGE, RabbitConfig.NOTICE_KEY, msgJson);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * 连接建立成功调用
     *
     * @param session 客户端与socket建立的会话
     * @param session 客户端的userId
     */
    @OnOpen
    public void onOpen(Session session) {
        String accountId = validToken(session);
        if (StringUtils.isEmpty(accountId)) {
            return;
        }
        sessionPools.remove(accountId);
        sessionPools.put(accountId, session);

        NoticeService noticeService = SpringUtil.getBean(NoticeService.class);
        if( noticeService != null ){
            List<NoticePO> noticePOList = noticeService.findUnreadMessages(accountId);
            if( noticePOList != null && !noticePOList.isEmpty() ){
                for( NoticePO noticePO : noticePOList ){
                    boolean isSuccess = pushMessage(noticePO);
                    if( isSuccess ){
                        // 推送成功后，将消息标记为已读
                        noticeService.markAsRead(noticePO.getId());
                    }
                }
            }
        }

    }

    /**
     * 关闭连接时调用
     *
     * @param session 关闭连接的客户端的姓名
     */
    @OnClose
    public void onClose(Session session) {
        String accountId = validToken(session);
        if (StringUtils.isEmpty(accountId)) {
            return;
        }
        sessionPools.remove(accountId);
    }


    /**
     * 发生错误时候
     *
     * @param session
     * @param throwable
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("发生错误");
        throwable.printStackTrace();
    }

    /**
     * 给指定用户发送消息
     *
     * @param noticePO 需要推送的消息
     * @throws IOException
     */
    public boolean pushMessage(NoticePO noticePO) {
        //获取当前会话
        Session session = sessionPools.get(noticePO.getReceiverId());
        if (null != session && null != noticePO) {
            //获取消息体
            return sendMessage(session, JSON.toJSONString(noticePO));
        }
        return false;
    }

    /**
     * 发送消息
     *
     * @param session
     * @param message
     */
    private boolean sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 批量发送消息
     *
     * @param messageBOList
     */
    public void pushMessage(List<NoticePO> messageBOList) {
        if (null != messageBOList && !messageBOList.isEmpty()) {
            for (NoticePO noticePO : messageBOList) {
                pushMessage(noticePO);
            }
        }
    }

    private String validToken(Session session) {
        String token = getSessionToken(session);
        RedisSessionHelper redisSessionHelper = SpringUtil.getBean(RedisSessionHelper.class);
        if (null == redisSessionHelper) {
            return null;
        }
        SessionContext context = redisSessionHelper.getSession(token);
        boolean isisValid = redisSessionHelper.isValid(context);
        if (isisValid) {
            return context.getAccountID();
        }
        return null;
    }

    private String getSessionToken(Session session) {
        Map<String, List<String>> paramMap = session.getRequestParameterMap();
        List<String> paramList = paramMap.get(HtichConstants.SESSION_TOKEN_KEY);
        return LocalCollectionUtils.getOne(paramList);
    }
}