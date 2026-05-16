package com.expenseflow.approval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RuleInput {
    private String businessType;
    private double amount;
    private List<ExpenseItemInput> items = new ArrayList<>();
    private List<InvoiceInput> invoices = new ArrayList<>();
    private ApplicantHistory history;
    private List<PolicyInput> policies = new ArrayList<>();
}
