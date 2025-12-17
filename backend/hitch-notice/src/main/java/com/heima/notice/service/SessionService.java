package com.heima.notice.service;

import com.alibaba.fastjson.JSON;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.model.NoticeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理服务（支持跨集群）
 */
@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${notice.broker-id:1000}")
    private Integer brokerId;

    /**
     * 保存用户会话
     */
    public void saveSession(String accountId, NoticeSession session) {
        try {
            String key = NoticeConstants.RedisConstants.USER_SESSION_PREFIX + accountId;
            String sessionJson = JSON.toJSONString(session);
            stringRedisTemplate.opsForValue().set(key, sessionJson, 1, TimeUnit.HOURS);
            
            logger.debug("保存会话到Redis: accountId={}, brokerId={}, sessionJson={}", 
                    accountId, session.getBrokerId(), sessionJson);
            
            // 添加到在线用户集合
            stringRedisTemplate.opsForSet().add(NoticeConstants.RedisConstants.ONLINE_USERS, accountId);
        } catch (Exception e) {
            logger.warn("保存会话到Redis失败: accountId={}, error={}", accountId, e.getMessage());
        }
    }

    /**
     * 获取用户会话
     */
    public NoticeSession getSession(String accountId) {
        try {
            String key = NoticeConstants.RedisConstants.USER_SESSION_PREFIX + accountId;
            String sessionStr = stringRedisTemplate.opsForValue().get(key);
            if (sessionStr != null) {
                return JSON.parseObject(sessionStr, NoticeSession.class);
            }
            return null;
        } catch (Exception e) {
            logger.debug("获取会话时Redis不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 删除用户会话
     */
    public void removeSession(String accountId) {
        try {
            String key = NoticeConstants.RedisConstants.USER_SESSION_PREFIX + accountId;
            stringRedisTemplate.delete(key);
            
            // 从在线用户集合移除
            stringRedisTemplate.opsForSet().remove(NoticeConstants.RedisConstants.ONLINE_USERS, accountId);
        } catch (Exception e) {
            // Redis可能已关闭，忽略错误
            logger.debug("删除会话时Redis不可用: {}", e.getMessage());
        }
    }

    /**
     * 更新心跳时间
     * 注意：只更新心跳时间，不修改brokerId（避免覆盖正确的节点信息）
     */
    public void updateHeartbeat(String accountId) {
        try {
            NoticeSession session = getSession(accountId);
            if (session != null) {
                // 记录更新前的brokerId，用于日志
                Integer oldBrokerId = session.getBrokerId();
                session.updateHeartbeat();
                saveSession(accountId, session);
                logger.debug("更新心跳: accountId={}, brokerId={} (保持不变)", accountId, oldBrokerId);
            }
        } catch (Exception e) {
            logger.debug("更新心跳时Redis不可用: {}", e.getMessage());
        }
    }

    /**
     * 获取所有在线用户ID
     */
    public List<String> getAllOnlineUsers() {
        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(NoticeConstants.RedisConstants.ONLINE_USERS);
            return new ArrayList<>(members != null ? members : new ArrayList<>());
        } catch (Exception e) {
            logger.debug("获取在线用户列表时Redis不可用: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String accountId) {
        try {
            NoticeSession session = getSession(accountId);
            return session != null && session.getConnectState() == 1;
        } catch (Exception e) {
            logger.debug("检查用户在线状态时Redis不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取用户所在的brokerId
     */
    public Integer getBrokerId(String accountId) {
        try {
            NoticeSession session = getSession(accountId);
            Integer brokerId = session != null ? session.getBrokerId() : null;
            logger.debug("获取用户brokerId: accountId={}, brokerId={}", accountId, brokerId);
            return brokerId;
        } catch (Exception e) {
            logger.debug("获取brokerId时Redis不可用: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前brokerId
     */
    public Integer getCurrentBrokerId() {
        return brokerId;
    }
}

