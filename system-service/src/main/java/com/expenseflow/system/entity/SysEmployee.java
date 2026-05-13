package com.expenseflow.system.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_employee")
public class SysEmployee extends BaseEntity {
    private Long userId;
    private Long departmentId;
    private String employeeNo;
    private String position;
    private LocalDate hireDate;
}
