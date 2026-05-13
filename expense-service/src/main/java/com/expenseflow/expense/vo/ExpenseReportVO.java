package com.expenseflow.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExpenseReportVO {

    private Long id;
    private Long tenantId;
    private String reportNo;
    private Long applicantId;
    private String applicantName;
    private Long departmentId;
    private String departmentName;
    private Long travelRequestId;
    private String travelRequestNo;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private LocalDate reportDate;
    private String remark;
    private String status;
    private String processInstanceId;
    private BigDecimal paidAmount;
    private LocalDateTime paidTime;
    private List<ExpenseItemVO> items;
    private LocalDateTime createTime;
}
