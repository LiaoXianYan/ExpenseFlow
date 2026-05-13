package com.expenseflow.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_audit_log")
public class SysAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String operation;
    private String module;
    private String targetType;
    private String targetId;
    private String requestParams;
    private String oldValue;
    private String newValue;
    private String ip;
    private String userAgent;
    private Long duration;
    private LocalDateTime createTime;
}
