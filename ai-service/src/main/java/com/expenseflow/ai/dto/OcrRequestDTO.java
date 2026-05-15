package com.expenseflow.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OcrRequestDTO {
    @NotNull(message = "发票ID不能为空")
    private Long invoiceId;
    private String imageUrl;
}
