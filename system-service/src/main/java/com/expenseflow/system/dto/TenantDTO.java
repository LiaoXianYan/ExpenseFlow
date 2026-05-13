package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantDTO {
    @NotBlank(message = "租户编码不能为空")
    private String tenantCode;
    @NotBlank(message = "租户名称不能为空")
    private String tenantName;
    private String contactName;
    private String contactPhone;
}
