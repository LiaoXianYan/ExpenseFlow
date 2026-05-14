package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.vo.ReviewResultVO;
import com.expenseflow.ai.vo.RiskReportVO;

public interface DeepSeekReviewService {
    ReviewResultVO review(ReviewRequestDTO dto, Long tenantId);
    RiskReportVO analyzeRisk(Long reportId, Long tenantId);
}
