package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_travel_request")
public class ExTravelRequest extends BaseEntity {
    private String requestNo;
    private Long applicantId;
    private Long departmentId;
    private String travelPurpose;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal estimatedAmount;
    private String companions;
    private String remark;
    private String status;
    private String processInstanceId;
}
