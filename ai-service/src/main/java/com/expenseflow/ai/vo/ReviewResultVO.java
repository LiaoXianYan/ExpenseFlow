package com.expenseflow.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReviewResultVO {
    private Long id;
    private String businessType;
    private Long businessId;
    private String model;
    private String reviewResult;
    private String riskLevel;
    private String reviewOpinion;
    private String riskReasons;
    private BigDecimal confidence;
    private Integer promptTokens;
    private Integer completionTokens;
    private Long processTimeMs;
    private LocalDateTime createTime;
}
