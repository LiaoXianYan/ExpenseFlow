package com.expenseflow.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TravelRequestDTO {

    @NotBlank(message = "出差目的不能为空")
    private String travelPurpose;

    @NotBlank(message = "目的地不能为空")
    private String destination;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private BigDecimal estimatedAmount;
    private String companions;
    private Long departmentId;
    private String remark;
}
