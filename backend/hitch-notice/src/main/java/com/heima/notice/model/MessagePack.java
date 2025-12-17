package com.heima.notice.model;

import java.io.Serializable;

/**
 * 消息包
 */
public class MessagePack implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 命令
     */
    private Integer command;

    /**
     * 接收者ID
     */
    private String toId;

    /**
     * 发送者ID
     */
    private String fromId;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息序列号
     */
    private Long messageSequence;

    /**
     * 消息内容
     */
    private Object data;

    /**
     * 时间戳
     */
    private Long timestamp;

    public MessagePack() {
        this.timestamp = System.currentTimeMillis();
    }

    public Integer getCommand() {
        return command;
    }

    public void setCommand(Integer command) {
        this.command = command;
    }

    public String getToId() {
        return toId;
    }

    public void setToId(String toId) {
        this.toId = toId;
    }

    public String getFromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
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

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}

