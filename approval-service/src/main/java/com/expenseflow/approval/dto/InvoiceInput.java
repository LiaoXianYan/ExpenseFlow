package com.expenseflow.approval.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class InvoiceInput {
    private String invoiceNo;
    private String invoiceCode;
    private LocalDate invoiceDate;
    private double amount;
    private String sellerName;
}
