package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_expense_report")
public class ExExpenseReport extends BaseEntity {
    private String reportNo;
    private Long applicantId;
    private Long departmentId;
    private Long travelRequestId;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private LocalDate reportDate;
    private String remark;
    private String status;
    private String processInstanceId;
    private BigDecimal paidAmount;
    private LocalDateTime paidTime;
}
