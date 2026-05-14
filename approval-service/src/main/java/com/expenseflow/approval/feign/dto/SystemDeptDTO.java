package com.expenseflow.approval.feign.dto;

import lombok.Data;

@Data
public class SystemDeptDTO {
    private Long id;
    private String deptName;
    private String deptCode;
}
