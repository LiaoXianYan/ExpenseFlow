package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseItemDTO {
    private Long id;
    private Long reportId;
    private String expenseType;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
}
