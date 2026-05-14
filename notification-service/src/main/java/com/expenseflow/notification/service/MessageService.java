package com.expenseflow.notification.service;

import com.expenseflow.common.result.Result;
import com.expenseflow.notification.vo.MessageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface MessageService {
    Result<Page<MessageVO>> page(int page, int size, Long userId);
    void send(Long userId, String title, String content, String messageType, String businessType, Long businessId, Long tenantId);
    void markRead(Long messageId);
    void markAllRead(Long userId);
    long unreadCount(Long userId);
}
