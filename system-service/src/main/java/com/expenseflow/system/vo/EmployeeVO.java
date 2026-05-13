package com.expenseflow.system.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EmployeeVO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long departmentId;
    private String employeeNo;
    private String position;
    private LocalDate hireDate;
    private LocalDateTime createTime;
}
