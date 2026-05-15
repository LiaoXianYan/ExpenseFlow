package com.expenseflow.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {
    private Long id;
    private Long userId;
    private String messageType;
    private String title;
    private String content;
    private String businessType;
    private Long businessId;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
