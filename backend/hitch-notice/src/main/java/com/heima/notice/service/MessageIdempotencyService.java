package com.heima.notice.service;

import com.alibaba.fastjson.JSON;
import com.heima.notice.constant.NoticeConstants;
import com.heima.modules.po.NoticePO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 消息幂等性服务（防止重复消息）
 */
@Service
public class MessageIdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(MessageIdempotencyService.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 检查消息是否已处理（幂等性检查）
     */
    public boolean isMessageProcessed(String messageId) {
        try {
            String key = NoticeConstants.RedisConstants.MESSAGE_ID_CACHE_PREFIX + messageId;
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.warn("检查消息幂等性时Redis不可用: {}", e.getMessage());
            return false; // Redis不可用时，允许处理（降级方案）
        }
    }

    /**
     * 标记消息已处理
     */
    public void markMessageProcessed(String messageId, NoticePO noticePO) {
        try {
            String key = NoticeConstants.RedisConstants.MESSAGE_ID_CACHE_PREFIX + messageId;
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(noticePO), 
                    NoticeConstants.Config.MESSAGE_ID_CACHE_EXPIRE, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("标记消息已处理时Redis不可用: {}", e.getMessage());
            // Redis不可用时，不标记（降级方案）
        }
    }

    /**
     * 获取已处理的消息（用于重试场景）
     */
    public NoticePO getProcessedMessage(String messageId) {
        try {
            String key = NoticeConstants.RedisConstants.MESSAGE_ID_CACHE_PREFIX + messageId;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                return JSON.parseObject(value, NoticePO.class);
            }
            return null;
        } catch (Exception e) {
            logger.warn("获取已处理消息时Redis不可用: {}", e.getMessage());
            return null;
        }
    }
}

