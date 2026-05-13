package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseItemDTO {

    @NotBlank(message = "费用类型不能为空")
    private String expenseType;

    @NotNull(message = "费用日期不能为空")
    private LocalDate expenseDate;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private String description;
    private Long invoiceId;
}
