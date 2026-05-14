package com.expenseflow.approval.dto;

import lombok.Data;

@Data
public class ApprovalCallbackDTO {
    private String businessType;
    private Long businessId;
    private String processInstanceId;
    private String outcome;
    private String comment;
}
