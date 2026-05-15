package com.expenseflow.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OcrResultVO {
    private Long id;
    private Long invoiceId;
    private String parsedInvoiceNo;
    private String parsedInvoiceCode;
    private BigDecimal parsedAmount;
    private LocalDate parsedInvoiceDate;
    private String parsedSellerName;
    private BigDecimal confidence;
    private String status;
    private LocalDateTime createTime;
}
