package com.heima.notice.constant;

/**
 * 通知服务常量定义
 */
public class NoticeConstants {

    /**
     * RabbitMQ 相关常量
     */
    public static class RabbitConstants {
        /**
         * 业务服务到WebSocket网关的Exchange
         */
        public static final String NOTICE_SERVICE_2_WS = "noticeService2WebSocket";
        
        /**
         * WebSocket网关到业务服务的Exchange
         */
        public static final String WS_2_NOTICE_SERVICE = "webSocket2NoticeService";
        
        /**
         * 消息存储队列
         */
        public static final String STORE_NOTICE_MESSAGE = "storeNoticeMessage";
    }

    /**
     * Redis 相关常量
     */
    public static class RedisConstants {
        /**
         * 用户会话前缀
         */
        public static final String USER_SESSION_PREFIX = "NOTICE:USER_SESSION:";
        
        /**
         * 消息序列号前缀
         */
        public static final String MESSAGE_SEQ_PREFIX = "NOTICE:MSG_SEQ:";
        
        /**
         * 消息ID缓存前缀（用于去重）
         */
        public static final String MESSAGE_ID_CACHE_PREFIX = "NOTICE:MSG_ID_CACHE:";
        
        /**
         * 消息确认等待前缀（用于双ACK机制）
         */
        public static final String MESSAGE_ACK_WAIT_PREFIX = "NOTICE:MSG_ACK_WAIT:";
        
        /**
         * 在线用户集合
         */
        public static final String ONLINE_USERS = "NOTICE:ONLINE_USERS";
        
        /**
         * 离线消息前缀（Redis ZSet）
         */
        public static final String OFFLINE_MESSAGE_PREFIX = "NOTICE:OFFLINE_MSG:";
    }

    /**
     * WebSocket 消息命令
     */
    public static class Command {
        /**
         * 发送消息
         */
        public static final int SEND_MESSAGE = 1001;
        
        /**
         * 消息ACK（客户端收到消息后确认）
         */
        public static final int MESSAGE_ACK = 1002;
        
        /**
         * 服务端ACK（服务端收到客户端ACK后确认）
         */
        public static final int SERVER_ACK = 1003;
        
        /**
         * 心跳
         */
        public static final int PING = 1004;
        
        /**
         * 心跳响应
         */
        public static final int PONG = 1005;
        
        /**
         * 消息推送
         */
        public static final int PUSH_MESSAGE = 1006;
    }

    /**
     * 消息状态
     */
    public static class MessageStatus {
        /**
         * 已发送
         */
        public static final int SENT = 1;
        
        /**
         * 已送达（客户端ACK）
         */
        public static final int DELIVERED = 2;
        
        /**
         * 已确认（服务端ACK）
         */
        public static final int CONFIRMED = 3;
        
        /**
         * 发送失败
         */
        public static final int FAILED = 4;
    }

    /**
     * 配置常量
     */
    public static class Config {
        /**
         * 心跳超时时间（毫秒）
         */
        public static final long HEARTBEAT_TIMEOUT = 30000;
        
        /**
         * 消息ACK超时时间（毫秒）
         */
        public static final long MESSAGE_ACK_TIMEOUT = 10000;
        
        /**
         * 消息ID缓存过期时间（秒）
         */
        public static final long MESSAGE_ID_CACHE_EXPIRE = 300;
        
        /**
         * 消息重试次数
         */
        public static final int MESSAGE_RETRY_COUNT = 3;
    }
}

