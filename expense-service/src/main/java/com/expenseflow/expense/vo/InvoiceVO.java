package com.expenseflow.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InvoiceVO {

    private Long id;
    private Long tenantId;
    private String invoiceNo;
    private String invoiceCode;
    private String invoiceType;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String sellerName;
    private String buyerName;
    private String imageUrl;
    private String ocrStatus;
    private BigDecimal ocrConfidence;
    private String verifyStatus;
    private LocalDateTime createTime;
}
