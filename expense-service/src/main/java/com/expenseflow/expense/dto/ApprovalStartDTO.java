package com.expenseflow.expense.dto;

import lombok.Data;

@Data
public class ApprovalStartDTO {

    private String businessType;
    private Long businessId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private java.math.BigDecimal amount;
    private Long departmentId;
}
