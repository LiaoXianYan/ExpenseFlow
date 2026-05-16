package com.expenseflow.approval.dto;

import lombok.Data;

@Data
public class PolicyInput {
    private String expenseType;
    private double maxAmount;
    private double dailyLimit;
    private String cityTier;
}
