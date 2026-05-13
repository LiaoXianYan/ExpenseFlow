package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpensePolicyDTO {

    @NotBlank(message = "政策名称不能为空")
    private String policyName;

    @NotBlank(message = "费用类型不能为空")
    private String expenseType;

    @NotNull(message = "单次上限不能为空")
    private BigDecimal maxAmount;

    private BigDecimal dailyLimit;
    private String cityTier;

    @NotNull(message = "生效日期不能为空")
    private LocalDate effectiveDate;

    private LocalDate expireDate;
    private String remark;
}
