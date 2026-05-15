package com.expenseflow.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RuleInput {
    private String businessType;
    private double amount;
}
