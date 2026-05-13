package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_cost_record")
public class ExCostRecord extends BaseEntity {
    private Long userId;
    private LocalDate costDate;
    private String costType;
    private BigDecimal amount;
    private String description;
    private Long invoiceId;
    private Long travelRequestId;
    private Long reportId;
}
