package com.expenseflow.approval.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalTaskVO {
    private String taskId;
    private String taskName;
    private String taskDefinitionKey;
    private String processInstanceId;
    private String businessType;
    private Long businessId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private String assignee;
    private LocalDateTime createTime;
}
