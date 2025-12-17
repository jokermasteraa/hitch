package com.heima.notice.model;

import java.io.Serializable;

/**
 * 通知会话信息
 */
public class NoticeSession implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String accountId;

    /**
     * 节点ID（brokerId）
     */
    private Integer brokerId;

    /**
     * 连接状态 1=在线 2=离线
     */
    private Integer connectState;

    /**
     * 最后心跳时间
     */
    private Long lastHeartbeatTime;

    public NoticeSession() {
    }

    public NoticeSession(String accountId, Integer brokerId) {
        this.accountId = accountId;
        this.brokerId = brokerId;
        this.connectState = 1; // 在线
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Integer getBrokerId() {
        return brokerId;
    }

    public void setBrokerId(Integer brokerId) {
        this.brokerId = brokerId;
    }

    public Integer getConnectState() {
        return connectState;
    }

    public void setConnectState(Integer connectState) {
        this.connectState = connectState;
    }

    public Long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }

    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }
}

