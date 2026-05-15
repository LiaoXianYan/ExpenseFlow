package com.expenseflow.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApprovalStartDTO {
    @NotBlank(message = "业务类型不能为空")
    private String businessType;
    @NotNull(message = "业务ID不能为空")
    private Long businessId;
    @NotBlank(message = "业务编号不能为空")
    private String requestNo;
    @NotNull(message = "申请人ID不能为空")
    private Long applicantId;
    private String applicantName;
    private BigDecimal amount;
    private Long departmentId;
}
