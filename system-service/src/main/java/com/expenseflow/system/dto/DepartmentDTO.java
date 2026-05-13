package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentDTO {
    @NotBlank(message = "部门名称不能为空")
    private String deptName;
    private String deptCode;
    private Long parentId;
    private Long leaderId;
    private Integer sortOrder;
}
