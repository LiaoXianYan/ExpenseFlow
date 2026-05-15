package com.expenseflow.notification.service;

import com.expenseflow.notification.entity.NtMessage;
import com.expenseflow.notification.mapper.NtMessageMapper;
import com.expenseflow.notification.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock NtMessageMapper messageMapper;
    @InjectMocks MessageServiceImpl messageService;

    @Test
    @DisplayName("发送消息应写入数据库")
    void shouldInsertMessageOnSend() {
        when(messageMapper.insert(any(NtMessage.class))).thenReturn(1);

        assertDoesNotThrow(() ->
            messageService.send(1L, "标题", "内容", "NOTIFICATION", "EXPENSE_REPORT", 100L, 0L));
        verify(messageMapper).insert(any(NtMessage.class));
    }

    @Test
    @DisplayName("标为已读")
    void shouldMarkAsRead() {
        NtMessage msg = new NtMessage();
        msg.setId(1L); msg.setIsRead(0);
        when(messageMapper.selectById(1L)).thenReturn(msg);
        when(messageMapper.updateById(any(NtMessage.class))).thenReturn(1);

        assertDoesNotThrow(() -> messageService.markRead(1L));
        verify(messageMapper).updateById(any(NtMessage.class));
    }

    @Test
    @DisplayName("全部标为已读")
    void shouldMarkAllAsRead() {
        NtMessage msg = new NtMessage();
        msg.setId(1L); msg.setIsRead(0);
        when(messageMapper.selectList(any())).thenReturn(java.util.List.of(msg));
        when(messageMapper.updateById(any(NtMessage.class))).thenReturn(1);

        assertDoesNotThrow(() -> messageService.markAllRead(1L));
    }
}
