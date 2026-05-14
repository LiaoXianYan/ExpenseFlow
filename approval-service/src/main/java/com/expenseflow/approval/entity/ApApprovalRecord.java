package com.expenseflow.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ap_approval_record")
public class ApApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String businessType;
    private Long businessId;
    private String processInstanceId;
    private String taskId;
    private String taskName;
    private Long approverId;
    private String approverName;
    private String action;
    private String comment;
    private LocalDateTime actionTime;
    private LocalDateTime createTime;
}
