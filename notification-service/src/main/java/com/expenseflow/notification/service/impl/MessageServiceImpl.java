package com.expenseflow.notification.service.impl;

import com.expenseflow.common.result.Result;
import com.expenseflow.notification.entity.NtMessage;
import com.expenseflow.notification.mapper.NtMessageMapper;
import com.expenseflow.notification.service.MessageService;
import com.expenseflow.notification.vo.MessageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final NtMessageMapper messageMapper;

    @Override
    public Result<Page<MessageVO>> page(int page, int size, Long userId) {
        LambdaQueryWrapper<NtMessage> qw = new LambdaQueryWrapper<>();
        if (userId != null) qw.eq(NtMessage::getUserId, userId);
        qw.orderByDesc(NtMessage::getCreateTime);
        Page<NtMessage> pg = messageMapper.selectPage(new Page<>(page, size), qw);
        Page<MessageVO> voPage = new Page<>(page, size, pg.getTotal());
        voPage.setRecords(pg.getRecords().stream().map(m -> {
            MessageVO vo = new MessageVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).toList());
        return Result.ok(voPage);
    }

    @Override
    @Transactional
    public void send(Long userId, String title, String content, String messageType,
                     String businessType, Long businessId, Long tenantId) {
        NtMessage msg = new NtMessage();
        msg.setTenantId(tenantId);
        msg.setUserId(userId);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : "NOTIFICATION");
        msg.setBusinessType(businessType);
        msg.setBusinessId(businessId);
        msg.setIsRead(0);
        messageMapper.insert(msg);
        log.info("站内消息发送: userId={}, title={}", userId, title);
    }

    @Override
    @Transactional
    public void markRead(Long messageId) {
        NtMessage msg = messageMapper.selectById(messageId);
        if (msg != null && msg.getIsRead() == 0) {
            msg.setIsRead(1);
            msg.setReadTime(LocalDateTime.now());
            messageMapper.updateById(msg);
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        messageMapper.selectList(
            new LambdaQueryWrapper<NtMessage>()
                .eq(NtMessage::getUserId, userId)
                .eq(NtMessage::getIsRead, 0)
        ).forEach(msg -> {
            msg.setIsRead(1);
            msg.setReadTime(LocalDateTime.now());
            messageMapper.updateById(msg);
        });
    }

    @Override
    public long unreadCount(Long userId) {
        return messageMapper.selectCount(
            new LambdaQueryWrapper<NtMessage>()
                .eq(NtMessage::getUserId, userId)
                .eq(NtMessage::getIsRead, 0));
    }
}
