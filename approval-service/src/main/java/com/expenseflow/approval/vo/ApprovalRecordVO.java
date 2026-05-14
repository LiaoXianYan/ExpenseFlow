package com.expenseflow.approval.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalRecordVO {
    private Long id;
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
