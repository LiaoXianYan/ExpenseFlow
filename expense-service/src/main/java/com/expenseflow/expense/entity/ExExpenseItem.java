package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_item")
public class ExExpenseItem extends BaseEntity {
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
}
