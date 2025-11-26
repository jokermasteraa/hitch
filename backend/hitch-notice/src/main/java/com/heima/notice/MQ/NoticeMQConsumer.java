package com.heima.notice.MQ;

import com.alibaba.fastjson.JSON;
import com.heima.commons.utils.CommonsUtils;
import com.heima.modules.po.NoticePO;
import com.heima.modules.vo.NoticeVO;
import com.heima.notice.configuration.RabbitConfig;
import com.heima.notice.service.NoticeService;
import com.heima.notice.socket.WebSocketServer;
import com.rabbitmq.client.Channel; // 引入 Channel
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NoticeMQConsumer {

    private static final Logger log = LoggerFactory.getLogger(NoticeMQConsumer.class);
    @Autowired
    private WebSocketServer webSocketServer;

    @Autowired
    private NoticeService noticeService;

    /**
     * 加上手动确认模式
     * 需要在 application.yml 中配置: spring.rabbitmq.listener.simple.acknowledge-mode: manual
     */
    @RabbitListener(queues = RabbitConfig.NOTICE_QUEUE)
    @RabbitHandler
    public void receiveMessage(String msg, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            NoticeVO noticeVO = JSON.parseObject(msg, NoticeVO.class);
            if (noticeVO == null) {
                // 消息格式错误，直接丢弃，别重试了
                channel.basicAck(tag, false);
                return;
            }

            NoticePO noticePO = CommonsUtils.toPO(noticeVO);


            // 【幂等性检查 - 简易版】
            // 如果你的 NoticePO 里有 status 字段，可以在这里查库判断 if (status == SENT) return;
            // 但为了性能，通常建议交给客户端去重，或者这里直接推，相信 WebSocket 的并发处理能力

            // 1. 尝试实时推送
            boolean pushSuccess = webSocketServer.pushMessage(noticePO);

            long receivedTime = System.currentTimeMillis();
            long sendTime = noticeVO.getSendTime();

            long mqLatency = receivedTime - sendTime;
            log.info("MQ延迟: {}ms", mqLatency);

            // 2. 只有推送成功了，才去标记已读
            if (pushSuccess) {
                noticeService.markAsRead(noticePO.getId());
            }

            log.info("推送结果: {}", pushSuccess);
            // 如果失败了，不做任何事，保留 read=false，等待 onOpen 自动拉取

            // 3. 总是 ACK
            channel.basicAck(tag, false);

        } catch (Exception e) {
            e.printStackTrace();
            // 异常也 ACK，防止阻塞队列
            try {
                channel.basicAck(tag, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}