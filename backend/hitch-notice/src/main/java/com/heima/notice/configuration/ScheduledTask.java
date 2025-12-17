package com.heima.notice.configuration;

import com.heima.modules.po.NoticePO;
import com.heima.notice.service.NoticeService;
import com.heima.notice.service.SessionService;
import com.heima.notice.socket.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务 - 推送离线消息（保留用于离线消息推送）
 * 注意：实时消息现在通过RabbitMQ路由，不再需要轮询
 */
@Component
public class ScheduledTask {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTask.class);

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private WebSocketServer webSocketServer;

    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void init() {
        // 定时任务已禁用：实时消息通过RabbitMQ实时推送，不需要定时轮询
        // 如果启用定时任务，会导致重复推送（实时推送的消息read=false，定时任务会重复推送）
        // executorService.execute(() -> {
        //     autoPushOfflineMessage();
        // });
        logger.info("定时任务已禁用：实时消息通过RabbitMQ实时推送，避免重复推送");
    }

    @PreDestroy
    public void destroy() {
        running.set(false);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 自动推送离线消息（用户上线后推送未读消息）
     */
    public void autoPushOfflineMessage() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 获取所有在线用户
                List<String> onlineUsers = sessionService.getAllOnlineUsers();
                
                if (onlineUsers != null && !onlineUsers.isEmpty()) {
                    // 获取每个用户的未读消息
                    List<NoticePO> unreadMessages = noticeService.getNoticeByAccountIds(onlineUsers);
                    
                    if (unreadMessages != null && !unreadMessages.isEmpty()) {
                        logger.debug("推送离线消息，数量: {}", unreadMessages.size());
                        // 推送消息
                        webSocketServer.pushMessage(unreadMessages);
                    }
                }
                
                // 每5秒检查一次
                TimeUnit.SECONDS.sleep(5);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 如果是关闭过程中的异常，忽略
                if (!running.get()) {
                    break;
                }
                logger.error("推送离线消息失败: {}", e.getMessage(), e);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
