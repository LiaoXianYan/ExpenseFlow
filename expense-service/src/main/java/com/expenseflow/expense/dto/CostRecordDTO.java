package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CostRecordDTO {

    @NotNull(message = "费用日期不能为空")
    private LocalDate costDate;

    @NotBlank(message = "费用类型不能为空")
    private String costType;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    private String description;
    private Long invoiceId;
    private Long travelRequestId;
    private Long reportId;
}
