package com.expenseflow.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "部门ID不能为空")
    private Long departmentId;
    private String employeeNo;
    private String position;
    private LocalDate hireDate;
}
