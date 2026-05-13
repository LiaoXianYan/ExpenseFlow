package com.expenseflow.expense.entity;

import com.expenseflow.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ex_invoice")
public class ExInvoice extends BaseEntity {
    private String invoiceNo;
    private String invoiceCode;
    private String invoiceType;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String sellerName;
    private String sellerTaxNo;
    private String buyerName;
    private String buyerTaxNo;
    private String imageUrl;
    private String ocrStatus;
    private String ocrRawResult;
    private BigDecimal ocrConfidence;
    private String verifyStatus;
}
