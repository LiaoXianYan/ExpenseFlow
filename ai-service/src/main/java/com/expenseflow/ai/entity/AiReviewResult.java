package com.expenseflow.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_review_result")
public class AiReviewResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String businessType;
    private Long businessId;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private String reviewResult;
    private String riskLevel;
    private String reviewOpinion;
    private String riskReasons;
    private BigDecimal confidence;
    private Long processTimeMs;
    private LocalDateTime createTime;
}
