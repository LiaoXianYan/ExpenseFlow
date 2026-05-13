package com.expenseflow.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PolicyVO {

    private Long id;
    private Long tenantId;
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
