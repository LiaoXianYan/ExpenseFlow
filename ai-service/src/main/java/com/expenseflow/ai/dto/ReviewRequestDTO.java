package com.expenseflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ReviewRequestDTO {
    @NotBlank(message = "业务类型不能为空")
    private String businessType;
    @NotNull(message = "业务ID不能为空")
    private Long businessId;
    private String requestNo;
    private BigDecimal totalAmount;
    private List<ExpenseItemData> items;

    @Data
    public static class ExpenseItemData {
        private String expenseType;
        private BigDecimal amount;
        private String description;
    }
}
