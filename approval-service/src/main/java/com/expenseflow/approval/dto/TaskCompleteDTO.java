package com.expenseflow.approval.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskCompleteDTO {
    @NotBlank(message = "操作不能为空")
    private String action;

    private String comment;

    private String delegateToUser;
}
