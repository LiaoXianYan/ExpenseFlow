package com.expenseflow.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.expenseflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ap_approval_record")
public class ApApprovalRecord extends BaseEntity {
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
}
