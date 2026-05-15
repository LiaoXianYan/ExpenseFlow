package com.expenseflow.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProcessStartResponse {
    private String processInstanceId;
    private String approvalLevel;
    private List<String> warnings;
}
