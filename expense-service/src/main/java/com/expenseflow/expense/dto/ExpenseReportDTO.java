package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseReportDTO {

    @NotNull(message = "报销日期不能为空")
    private LocalDate reportDate;

    private Long travelRequestId;
    private Long departmentId;
    private String remark;
}
