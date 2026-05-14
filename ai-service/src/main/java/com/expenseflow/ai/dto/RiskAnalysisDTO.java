package com.expenseflow.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RiskAnalysisDTO {
    @NotNull(message = "报销单ID不能为空")
    private Long reportId;
    private BigDecimal totalAmount;
    private List<ReviewRequestDTO.ExpenseItemData> items;
}
