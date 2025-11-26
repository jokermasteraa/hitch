package com.heima.notice.service;


import com.heima.modules.po.NoticePO;
import com.heima.modules.vo.NoticeVO;

import java.util.List;

public interface NoticeService {

    public void addNotice(NoticePO message);

    public List<NoticePO> getNoticeByAccountIds(List<String> accountIds);

    List<NoticePO> queryList(NoticeVO noticeVO);

    // 【新增】查询用户的未读消息
    List<NoticePO> findUnreadMessages(String receiverId);

    // 【新增】标记消息为已读
    void markAsRead(String noticeId);
}
