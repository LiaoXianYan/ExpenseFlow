package com.expenseflow.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("nt_notification_template")
public class NtNotificationTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String templateCode;
    private String templateName;
    private String channel;
    private String titleTemplate;
    private String contentTemplate;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
