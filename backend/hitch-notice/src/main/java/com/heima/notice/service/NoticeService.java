package com.heima.notice.service;


import com.heima.modules.po.NoticePO;
import com.heima.modules.vo.NoticeVO;

import java.util.List;

public interface NoticeService {

    public void addNotice(NoticePO message);

    public List<NoticePO> getNoticeByAccountIds(List<String> accountIds);

    List<NoticePO> queryList(NoticeVO noticeVO);
    
    /**
     * 存储离线消息到 Redis ZSet（参考 l-im 实现）
     * 不管用户是否在线，总是存储离线消息
     */
    void storeOfflineMessage(NoticePO noticePO);
    
    /**
     * 获取离线消息（用户上线后拉取）
     * @param accountId 用户ID
     * @param lastSequence 最后同步的序列号（用于增量同步）
     * @param limit 限制数量
     * @return 离线消息列表
     */
    List<NoticePO> getOfflineMessages(String accountId, Long lastSequence, int limit);
    
    /**
     * 删除离线消息（推送成功后删除）
     */
    void removeOfflineMessage(String accountId, String messageId);
}
