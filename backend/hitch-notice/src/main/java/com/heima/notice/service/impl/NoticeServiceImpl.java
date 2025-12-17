package com.heima.notice.service.impl;


import com.alibaba.fastjson.JSON;
import com.heima.commons.constant.HtichConstants;
import com.heima.modules.po.NoticePO;
import com.heima.modules.vo.NoticeVO;
import com.heima.notice.constant.NoticeConstants;
import com.heima.notice.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class NoticeServiceImpl implements NoticeService {

    private static final Logger logger = LoggerFactory.getLogger(NoticeServiceImpl.class);
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
    // 离线消息队列最大数量（参考 l-im）
    private static final int MAX_OFFLINE_MESSAGE_COUNT = 1000;

    @Override
    public void addNotice(NoticePO noticePO) {
        try {
            noticePO.setCreatedTime(new Date());
            //mongoDB 保存消息
            mongoTemplate.save(noticePO, HtichConstants.NOTICE_COLLECTION);
        } catch (Exception e) {
            // MongoDB超时或不可用时，记录日志但不抛出异常
            // 消息已经通过RabbitMQ路由，即使MongoDB失败也不影响实时推送
            org.slf4j.LoggerFactory.getLogger(NoticeServiceImpl.class)
                .error("保存消息到MongoDB失败，消息ID: {}, 错误: {}", 
                    noticePO.getMessageId(), e.getMessage());
        }
    }

    /**
     * 根据用户ID 获取消息
     *
     * @param receiverIds
     * @return
     */
    @Override
    public List<NoticePO> getNoticeByAccountIds(List<String> receiverIds) {
        try {
            //根据用户ID获取消息 并获取前十条
            Criteria criteria = Criteria.where("receiverId").in(receiverIds);
            criteria.andOperator(Criteria.where("read").is(false));
            Query query = new Query(criteria);
            query.limit(10); // 限制查询数量

            Update update = Update.update("read", true);
            //查询并更新已读状态
            List<NoticePO> noticePOList = mongoTemplate.find(query, NoticePO.class, HtichConstants.NOTICE_COLLECTION);
            if (!noticePOList.isEmpty()) {
                mongoTemplate.updateMulti(query, update, NoticePO.class, HtichConstants.NOTICE_COLLECTION);
            }
            return noticePOList;
        } catch (Exception e) {
            // MongoDB超时或不可用时，返回空列表
            org.slf4j.LoggerFactory.getLogger(NoticeServiceImpl.class)
                .error("从MongoDB获取消息失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<NoticePO> queryList(NoticeVO noticeVO) {
        Criteria criteria = new Criteria();
        List<Criteria> orCriterias = new ArrayList<>();
        orCriterias.add(Criteria.where("receiverId").in(noticeVO.getReceiverId()).andOperator(Criteria.where("senderId").in(noticeVO.getSenderId())));
        orCriterias.add(Criteria.where("senderId").in(noticeVO.getReceiverId()).andOperator(Criteria.where("receiverId").in(noticeVO.getSenderId())));
        criteria.orOperator(orCriterias.toArray(new Criteria[0]));
        Query query = new Query(criteria);
        query.limit(20);
        query.with(Sort.by(Sort.Order.desc("createdTime")));
        List<NoticePO> noticePOList = mongoTemplate.find(query, NoticePO.class, HtichConstants.NOTICE_COLLECTION);
        Collections.reverse(noticePOList);
        return noticePOList;
    }

    /**
     * 存储离线消息到 Redis ZSet（参考 l-im 实现）
     * 不管用户是否在线，总是存储离线消息
     */
    @Override
    public void storeOfflineMessage(NoticePO noticePO) {
        try {
            String receiverId = noticePO.getReceiverId();
            if (receiverId == null) {
                logger.warn("存储离线消息失败：receiverId为空");
                return;
            }
            
            String key = NoticeConstants.RedisConstants.OFFLINE_MESSAGE_PREFIX + receiverId;
            ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
            
            // 使用 messageSequence 作为分值（保证有序）
            Long score = noticePO.getMessageSequence() != null ? noticePO.getMessageSequence() : System.currentTimeMillis();
            
            // 存储离线消息
            operations.add(key, JSON.toJSONString(noticePO), score);
            
            // 限制队列大小（超过配置值，删除最旧的消息）
            Long count = operations.zCard(key);
            if (count != null && count > MAX_OFFLINE_MESSAGE_COUNT) {
                operations.removeRange(key, 0, count - MAX_OFFLINE_MESSAGE_COUNT);
            }
            
            logger.debug("存储离线消息成功: receiverId={}, messageId={}, sequence={}", 
                    receiverId, noticePO.getMessageId(), score);
        } catch (Exception e) {
            logger.error("存储离线消息失败: receiverId={}, messageId={}, error={}", 
                    noticePO.getReceiverId(), noticePO.getMessageId(), e.getMessage(), e);
        }
    }

    /**
     * 获取离线消息（用户上线后拉取）
     */
    @Override
    public List<NoticePO> getOfflineMessages(String accountId, Long lastSequence, int limit) {
        try {
            String key = NoticeConstants.RedisConstants.OFFLINE_MESSAGE_PREFIX + accountId;
            ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
            
            // 获取最大的序列号（最新消息）
            Long maxSeq = 0L;
            Set<ZSetOperations.TypedTuple<String>> maxSet = operations.reverseRangeWithScores(key, 0, 0);
            if (maxSet != null && !maxSet.isEmpty()) {
                ZSetOperations.TypedTuple<String> tuple = maxSet.iterator().next();
                if (tuple.getScore() != null) {
                    maxSeq = tuple.getScore().longValue();
                }
            }
            
            // 根据 lastSequence 查询未同步的消息（增量同步）
            Set<ZSetOperations.TypedTuple<String>> querySet = operations.rangeByScoreWithScores(
                    key, lastSequence + 1, maxSeq, 0, limit);
            
            List<NoticePO> offlineMessages = new ArrayList<>();
            if (querySet != null) {
                for (ZSetOperations.TypedTuple<String> tuple : querySet) {
                    String value = tuple.getValue();
                    if (value != null) {
                        try {
                            NoticePO noticePO = JSON.parseObject(value, NoticePO.class);
                            offlineMessages.add(noticePO);
                        } catch (Exception e) {
                            logger.error("解析离线消息失败: value={}, error={}", value, e.getMessage());
                        }
                    }
                }
            }
            
            logger.debug("获取离线消息: accountId={}, lastSequence={}, limit={}, count={}", 
                    accountId, lastSequence, limit, offlineMessages.size());
            
            return offlineMessages;
        } catch (Exception e) {
            logger.error("获取离线消息失败: accountId={}, error={}", accountId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 删除离线消息（推送成功后删除）
     */
    @Override
    public void removeOfflineMessage(String accountId, String messageId) {
        try {
            String key = NoticeConstants.RedisConstants.OFFLINE_MESSAGE_PREFIX + accountId;
            ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
            
            // 获取所有消息，找到对应的 messageId 并删除
            Set<String> messages = operations.range(key, 0, -1);
            if (messages != null) {
                for (String messageStr : messages) {
                    try {
                        NoticePO noticePO = JSON.parseObject(messageStr, NoticePO.class);
                        if (messageId.equals(noticePO.getMessageId())) {
                            operations.remove(key, messageStr);
                            logger.debug("删除离线消息成功: accountId={}, messageId={}", accountId, messageId);
                            break;
                        }
                    } catch (Exception e) {
                        logger.error("解析离线消息失败: messageStr={}, error={}", messageStr, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("删除离线消息失败: accountId={}, messageId={}, error={}", 
                    accountId, messageId, e.getMessage(), e);
        }
    }

}
