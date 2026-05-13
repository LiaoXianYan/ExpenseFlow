package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_payment_record")
public class ExPaymentRecord extends BaseEntity {
    private Long reportId;
    private String paymentNo;
    private String payeeName;
    private String payeeAccount;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private LocalDateTime paymentTime;
    private Long operatorId;
    private String remark;
}
