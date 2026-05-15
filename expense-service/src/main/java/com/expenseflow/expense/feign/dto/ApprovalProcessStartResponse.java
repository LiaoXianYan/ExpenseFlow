package com.expenseflow.expense.feign.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApprovalProcessStartResponse {
    private String processInstanceId;
    private String approvalLevel;
    private List<String> warnings;
}
