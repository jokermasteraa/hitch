package com.heima.notice.service;

import com.heima.notice.constant.NoticeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 序列号生成服务（保证有序性）
 */
@Service
public class SequenceService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 获取消息序列号
     * 每个会话（senderId-receiverId）有独立的序列号
     */
    public Long getMessageSequence(String senderId, String receiverId) {
        try {
            // 生成会话ID（保证顺序，小的ID在前）
            String conversationId = generateConversationId(senderId, receiverId);
            String key = NoticeConstants.RedisConstants.MESSAGE_SEQ_PREFIX + conversationId;
            Long seq = stringRedisTemplate.opsForValue().increment(key);
            return seq != null ? seq : 0L;
        } catch (Exception e) {
            // Redis不可用时，使用时间戳作为序列号（降级方案）
            org.slf4j.LoggerFactory.getLogger(SequenceService.class)
                .warn("Redis不可用，使用时间戳作为序列号: {}", e.getMessage());
            return System.currentTimeMillis();
        }
    }

    /**
     * 生成会话ID（保证顺序）
     */
    private String generateConversationId(String senderId, String receiverId) {
        if (senderId.compareTo(receiverId) < 0) {
            return senderId + ":" + receiverId;
        } else {
            return receiverId + ":" + senderId;
        }
    }
}

