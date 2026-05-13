package com.expenseflow.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TravelRequestVO {

    private Long id;
    private Long tenantId;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private Long departmentId;
    private String departmentName;
    private String travelPurpose;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal estimatedAmount;
    private String companions;
    private String remark;
    private String status;
    private String processInstanceId;
    private LocalDateTime createTime;
}
