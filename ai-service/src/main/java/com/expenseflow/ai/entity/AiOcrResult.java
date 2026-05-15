package com.expenseflow.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_ocr_result")
public class AiOcrResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long invoiceId;
    private String requestId;
    private String rawResponse;
    private String parsedInvoiceNo;
    private String parsedInvoiceCode;
    private BigDecimal parsedAmount;
    private java.time.LocalDate parsedInvoiceDate;
    private String parsedSellerName;
    private String parsedSellerTaxNo;
    private String parsedBuyerName;
    private String parsedBuyerTaxNo;
    private BigDecimal confidence;
    private String status;
    private String errorMessage;
    private Long processTimeMs;
    private LocalDateTime createTime;
}
