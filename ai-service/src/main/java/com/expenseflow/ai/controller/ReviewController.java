package com.expenseflow.ai.controller;

import com.expenseflow.ai.BaseController;
import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.dto.RiskAnalysisDTO;
import com.expenseflow.ai.service.DeepSeekReviewService;
import com.expenseflow.ai.vo.ReviewResultVO;
import com.expenseflow.ai.vo.RiskReportVO;
import com.expenseflow.common.result.Result;
import com.expenseflow.ai.mapper.AiReviewResultMapper;
import com.expenseflow.ai.entity.AiReviewResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/ai/review")
@RequiredArgsConstructor
public class ReviewController extends BaseController {

    private final DeepSeekReviewService reviewService;
    private final AiReviewResultMapper reviewResultMapper;

    @PreAuthorize("hasAuthority('ai:review:execute')")
    @PostMapping("/evaluate")
    public Result<ReviewResultVO> evaluate(@Valid @RequestBody ReviewRequestDTO dto) {
        return Result.ok(reviewService.review(dto, getCurrentTenantId()));
    }

    @PreAuthorize("hasAuthority('ai:review:result')")
    @PostMapping("/risk")
    public Result<RiskReportVO> analyzeRisk(@Valid @RequestBody RiskAnalysisDTO dto) {
        return Result.ok(reviewService.analyzeRisk(dto.getReportId(), getCurrentTenantId()));
    }

    @GetMapping("/logs/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiReviewResult>> logsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reviewResultMapper.selectPage(
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
            new LambdaQueryWrapper<AiReviewResult>().orderByDesc(AiReviewResult::getCreateTime)));
    }

    @GetMapping("/alerts/recent")
    public Result<List<Map<String, Object>>> recentAlerts(@RequestParam(defaultValue = "7") int days,
                                                           @RequestParam(defaultValue = "5") int limit) {
        List<AiReviewResult> results = reviewResultMapper.selectList(
            new LambdaQueryWrapper<AiReviewResult>()
                .ge(AiReviewResult::getCreateTime, LocalDateTime.now().minusDays(days))
                .isNotNull(AiReviewResult::getRiskLevel)
                .orderByDesc(AiReviewResult::getCreateTime)
                .last("LIMIT " + limit));
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (AiReviewResult r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("description", r.getRiskReasons() != null ? r.getRiskReasons() : r.getRiskLevel());
            m.put("businessNo", r.getBusinessType() + "-" + r.getBusinessId());
            m.put("createdAt", r.getCreateTime());
            m.put("action", "HIGH".equalsIgnoreCase(r.getRiskLevel()) ? "BLOCK" : "WARN");
            alerts.add(m);
        }
        return Result.ok(alerts);
    }
}
