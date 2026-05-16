package com.expenseflow.approval.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExpenseItemInput {
    private String expenseType;
    private double amount;
    private LocalDate expenseDate;
}
