package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department_budget")
public class ExDepartmentBudget extends BaseEntity {
    private Long departmentId;
    private Integer budgetYear;
    private Integer budgetQuarter;
    private BigDecimal totalAmount;
    private BigDecimal usedAmount;
    private BigDecimal alertThreshold;
    private String status;
    private String remark;
    private Long createdBy;
}
