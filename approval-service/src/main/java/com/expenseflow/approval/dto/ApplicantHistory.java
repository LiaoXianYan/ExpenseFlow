package com.expenseflow.approval.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApplicantHistory {
    private Long applicantId;
    private int recentReportCount;
    private double avgAmount;
    private List<String> usedInvoiceNos = new ArrayList<>();
    private List<Long> usedCostRecordIds = new ArrayList<>();
    private boolean hasAmountDateVendorMatch;
    private boolean suspectedSplit;
}
