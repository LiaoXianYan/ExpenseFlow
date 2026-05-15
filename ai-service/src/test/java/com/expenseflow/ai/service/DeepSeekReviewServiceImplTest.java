package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.ReviewRequestDTO;
import com.expenseflow.ai.entity.AiReviewResult;
import com.expenseflow.ai.mapper.AiReviewResultMapper;
import com.expenseflow.ai.mapper.AiConfidenceStatsMapper;
import com.expenseflow.ai.service.impl.DeepSeekReviewServiceImpl;
import com.expenseflow.ai.vo.ReviewResultVO;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeepSeekReviewServiceImplTest {

    @Mock AiReviewResultMapper reviewMapper;
    @Mock AiConfidenceStatsMapper statsMapper;
    @Mock ChatLanguageModel chatModel;
    @InjectMocks DeepSeekReviewServiceImpl reviewService;

    @Test
    @DisplayName("API 正常返回时解析审单结果")
    void shouldParseReviewResultFromApi() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setBusinessType("EXPENSE_REPORT"); dto.setBusinessId(1L);
        dto.setTotalAmount(BigDecimal.valueOf(3000));

        when(chatModel.generate(anyString()))
            .thenReturn("结果:APPROVED 风险等级:LOW 意见:正常");
        when(reviewMapper.insert(any(AiReviewResult.class))).thenReturn(1);

        ReviewResultVO result = reviewService.review(dto, 0L);
        assertThat(result.getReviewResult()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("API 异常时降级到 Mock")
    void shouldFallbackToMockWhenApiFails() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setBusinessType("EXPENSE_REPORT"); dto.setBusinessId(1L);
        dto.setTotalAmount(BigDecimal.valueOf(15000));

        when(chatModel.generate(anyString()))
            .thenThrow(new RuntimeException("Connection refused"));
        when(reviewMapper.insert(any(AiReviewResult.class))).thenReturn(1);

        ReviewResultVO result = reviewService.review(dto, 0L);
        assertThat(result.getReviewResult()).isEqualTo("REVIEW_NEEDED");
    }

    @Test
    @DisplayName("小金额自动通过")
    void shouldAutoApproveSmallAmount() {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setBusinessType("EXPENSE_REPORT"); dto.setBusinessId(1L);
        dto.setTotalAmount(BigDecimal.valueOf(1000));

        when(chatModel.generate(anyString()))
            .thenReturn("结果:APPROVED 风险等级:LOW 意见:正常");
        when(reviewMapper.insert(any(AiReviewResult.class))).thenReturn(1);

        ReviewResultVO result = reviewService.review(dto, 0L);
        assertThat(result.getReviewResult()).isEqualTo("APPROVED");
    }
}
