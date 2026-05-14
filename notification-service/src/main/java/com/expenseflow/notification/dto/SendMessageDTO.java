package com.expenseflow.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    private String messageType;
    private String businessType;
    private Long businessId;
}
