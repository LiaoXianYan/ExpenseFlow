package com.expenseflow.approval.feign.dto;

import lombok.Data;
import java.util.List;

@Data
public class ApplicantHistoryDTO {
    private Long applicantId;
    private int recentReportCount;
    private double avgAmount;
    private List<String> usedInvoiceNos;
    private List<Long> usedCostRecordIds;
    private boolean hasAmountDateVendorMatch;
    private boolean suspectedSplit;
}
