package com.expenseflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagQuestionDTO {
    @NotBlank(message = "问题不能为空")
    private String question;
}
