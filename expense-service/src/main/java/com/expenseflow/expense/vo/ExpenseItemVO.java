package com.expenseflow.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseItemVO {

    private Long id;
    private Long tenantId;
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
    private LocalDateTime createTime;
}
