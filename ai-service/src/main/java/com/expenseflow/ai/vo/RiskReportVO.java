package com.expenseflow.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class RiskReportVO {
    private String riskLevel;
    private List<String> riskReasons;
    private String recommendation;
}
