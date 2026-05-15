package com.expenseflow.ai.controller;

import com.expenseflow.ai.BaseController;
import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.dto.RiskAnalysisDTO;
import com.expenseflow.ai.service.DeepSeekReviewService;
import com.expenseflow.ai.vo.ReviewResultVO;
import com.expenseflow.ai.vo.RiskReportVO;
import com.expenseflow.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/review")
@RequiredArgsConstructor
public class ReviewController extends BaseController {

    private final DeepSeekReviewService reviewService;

    @PostMapping("/evaluate")
    public Result<ReviewResultVO> evaluate(@Valid @RequestBody ReviewRequestDTO dto) {
        return Result.ok(reviewService.review(dto, getCurrentTenantId()));
    }

    @PostMapping("/risk")
    public Result<RiskReportVO> analyzeRisk(@Valid @RequestBody RiskAnalysisDTO dto) {
        return Result.ok(reviewService.analyzeRisk(dto.getReportId(), getCurrentTenantId()));
    }
}
