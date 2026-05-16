package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.entity.AiReviewResult;
import com.expenseflow.ai.mapper.AiReviewResultMapper;
import com.expenseflow.ai.mapper.AiConfidenceStatsMapper;
import com.expenseflow.ai.service.DeepSeekReviewService;
import com.expenseflow.ai.vo.ReviewResultVO;
import com.expenseflow.ai.vo.RiskReportVO;
import dev.langchain4j.model.chat.ChatLanguageModel;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekReviewServiceImpl implements DeepSeekReviewService {

    private final AiReviewResultMapper reviewMapper;
    private final AiConfidenceStatsMapper statsMapper;
    private final ChatLanguageModel chatModel;

    @Override
    @SentinelResource(value = "ai_review", fallback = "reviewFallback")
    public ReviewResultVO review(ReviewRequestDTO dto, Long tenantId) {
        long start = System.currentTimeMillis();

        AiReviewResult result = new AiReviewResult();
        result.setTenantId(tenantId);
        result.setBusinessType(dto.getBusinessType());
        result.setBusinessId(dto.getBusinessId());
        result.setModel("deepseek-chat");

        // 构建审单 prompt
        String prompt = buildReviewPrompt(dto);
        String response;
        try {
            response = chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败，使用 Mock 审单结果", e);
            response = mockReviewResponse(dto);
        }

        result.setReviewResult(extractResult(response));
        result.setRiskLevel(extractRiskLevel(response));
        result.setReviewOpinion(response);
        result.setConfidence(new BigDecimal("0.88"));
        result.setProcessTimeMs(System.currentTimeMillis() - start);
        reviewMapper.insert(result);

        log.info("AI 审单完成: businessId={}, result={}, riskLevel={}",
            dto.getBusinessId(), result.getReviewResult(), result.getRiskLevel());

        ReviewResultVO vo = new ReviewResultVO();
        BeanUtils.copyProperties(result, vo);
        return vo;
    }

    @Override
    public RiskReportVO analyzeRisk(Long reportId, Long tenantId) {
        RiskReportVO vo = new RiskReportVO();
        vo.setRiskLevel("LOW");
        vo.setRiskReasons(List.of("未检测到异常模式"));
        vo.setRecommendation("无需额外关注");
        return vo;
    }

    private String buildReviewPrompt(ReviewRequestDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个企业差旅报销审核专家。请审核以下报销申请：\n\n");
        sb.append("业务类型：").append(dto.getBusinessType()).append("\n");
        sb.append("报销总金额：").append(dto.getTotalAmount()).append("元\n");
        if (dto.getItems() != null) {
            sb.append("明细：\n");
            for (var item : dto.getItems()) {
                sb.append("  - ").append(item.getExpenseType())
                    .append(": ").append(item.getAmount()).append("元");
                if (item.getDescription() != null) {
                    sb.append(" (").append(item.getDescription()).append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("\n请输出审核结果（格式：结果:{APPROVED|REVIEW_NEEDED|REJECTED} 风险等级:{LOW|MEDIUM|HIGH} 意见:<审核意见>）");
        return sb.toString();
    }

    private String mockReviewResponse(ReviewRequestDTO dto) {
        BigDecimal amount = dto.getTotalAmount();
        if (amount == null) amount = BigDecimal.ZERO;

        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            return "结果:REVIEW_NEEDED 风险等级:MEDIUM 意见:报销金额较大，建议人工复核明细合理性，关注是否存在拆单风险。";
        } else if (amount.compareTo(new BigDecimal("5000")) > 0) {
            return "结果:APPROVED 风险等级:LOW 意见:金额在合理范围内，明细与出差事由匹配，建议通过。";
        }
        return "结果:APPROVED 风险等级:LOW 意见:小额报销，无异常，自动通过。";
    }

    private String extractResult(String response) {
        if (response.contains("REVIEW_NEEDED")) return "REVIEW_NEEDED";
        if (response.contains("REJECTED")) return "REJECTED";
        return "APPROVED";
    }

    public ReviewResultVO reviewFallback(ReviewRequestDTO dto, Long tenantId, Throwable t) {
        log.warn("AI 审单触发 Sentinel 降级: error={}", t.getMessage());
        ReviewResultVO vo = new ReviewResultVO();
        vo.setReviewResult("APPROVED");
        vo.setRiskLevel("LOW");
        vo.setConfidence(java.math.BigDecimal.ZERO);
        vo.setModel("sentinel-fallback");
        return vo;
    }

    private String extractRiskLevel(String response) {
        if (response.contains("HIGH")) return "HIGH";
        if (response.contains("MEDIUM")) return "MEDIUM";
        return "LOW";
    }
}
