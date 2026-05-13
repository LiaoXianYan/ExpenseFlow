package com.expenseflow.expense.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApprovalStartDTO {

    private String businessType;
    private Long businessId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
}
