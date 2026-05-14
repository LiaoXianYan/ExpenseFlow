package com.expenseflow.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nt_message")
public class NtMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
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
