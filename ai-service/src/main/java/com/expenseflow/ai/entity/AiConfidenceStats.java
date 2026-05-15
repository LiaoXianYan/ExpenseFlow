package com.expenseflow.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_confidence_stats")
public class AiConfidenceStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private LocalDate statDate;
    private Integer totalReviews;
    private Integer autoApproved;
    private Integer manualApproved;
    private Integer rejected;
    private Integer aiAdviceAdopted;
    private Integer aiAdviceOverridden;
    private BigDecimal avgConfidence;
    private Long avgProcessTimeMs;
    private LocalDateTime createTime;
}
