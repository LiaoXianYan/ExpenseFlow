package com.expenseflow.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TemplateDTO {
    @NotBlank(message = "模板编码不能为空")
    private String templateCode;
    @NotBlank(message = "模板名称不能为空")
    private String templateName;
    @NotBlank(message = "渠道不能为空")
    private String channel;
    @NotBlank(message = "标题模板不能为空")
    private String titleTemplate;
    @NotBlank(message = "内容模板不能为空")
    private String contentTemplate;
}
