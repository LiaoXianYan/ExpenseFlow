package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_policy")
public class ExExpensePolicy extends BaseEntity {
    private String policyName;
    private String expenseType;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private String cityTier;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer status;
    private String remark;
}
