package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.mapper.AiReviewResultMapper;
import com.expenseflow.ai.service.DeepSeekReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQConsumer {

    private final DeepSeekReviewService reviewService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "ai.review.queue")
    public void onReportSubmitted(Map<String, Object> map) {
        log.info("收到 AI 审单消息: {}", map);
        try {
            ReviewRequestDTO dto = new ReviewRequestDTO();
            dto.setBusinessType("EXPENSE_REPORT");
            dto.setBusinessId(Long.valueOf(map.get("reportId").toString()));
            Object amountObj = map.get("amount");
            dto.setTotalAmount(amountObj != null ? new BigDecimal(amountObj.toString()) : BigDecimal.ZERO);

            reviewService.review(dto, getTenantId(map));
        } catch (Exception e) {
            log.error("AI 审单消息处理失败", e);
        }
    }

    private Long getTenantId(Map<String, Object> map) {
        Object tid = map.get("tenantId");
        return tid != null ? Long.valueOf(tid.toString()) : 0L;
    }
}
