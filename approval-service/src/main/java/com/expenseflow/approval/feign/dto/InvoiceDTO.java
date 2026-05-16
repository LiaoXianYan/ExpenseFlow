package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceDTO {
    private Long id;
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private String sellerName;
}
