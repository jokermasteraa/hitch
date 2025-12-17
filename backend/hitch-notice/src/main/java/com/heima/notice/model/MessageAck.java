package com.heima.notice.model;

import java.io.Serializable;

/**
 * 消息确认
 */
public class MessageAck implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息序列号
     */
    private Long messageSequence;

    /**
     * ACK类型：1=客户端ACK，2=服务端ACK
     */
    private Integer ackType;

    /**
     * 时间戳
     */
    private Long timestamp;

    public MessageAck() {
        this.timestamp = System.currentTimeMillis();
    }

    public MessageAck(String messageId, Long messageSequence, Integer ackType) {
        this.messageId = messageId;
        this.messageSequence = messageSequence;
        this.ackType = ackType;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Long getMessageSequence() {
        return messageSequence;
    }

    public void setMessageSequence(Long messageSequence) {
        this.messageSequence = messageSequence;
    }

    public Integer getAckType() {
        return ackType;
    }

    public void setAckType(Integer ackType) {
        this.ackType = ackType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}

