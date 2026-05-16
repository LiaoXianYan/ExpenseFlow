package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExpensePolicyDTO {
    private Long id;
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private Integer status;
}
