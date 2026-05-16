package com.expenseflow.ai.controller;

import com.expenseflow.ai.BaseControllerTest;
import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.dto.ReviewRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AIControllerTest extends BaseControllerTest {

    @BeforeEach
    void setUpMockAi() {
        when(chatModel.generate(anyString()))
            .thenReturn("结果:APPROVED 风险等级:LOW 意见:金额合理，建议自动通过。");
    }

    @Test
    @DisplayName("POST /ai/review/evaluate — AI审单返回审核结果")
    void shouldEvaluateReview() throws Exception {
        ReviewRequestDTO dto = new ReviewRequestDTO();
        dto.setBusinessType("EXPENSE_REPORT");
        dto.setBusinessId(1L);
        dto.setTotalAmount(BigDecimal.valueOf(3000));

        postWithJwt("/ai/review/evaluate", dto, 1L, "USER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.businessType").value("EXPENSE_REPORT"))
            .andExpect(jsonPath("$.data.reviewResult").value("APPROVED"))
            .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
            .andExpect(jsonPath("$.data.model").value("deepseek-chat"));
    }

    @Test
    @DisplayName("POST /ai/rag/ask — RAG问答返回政策回答")
    void shouldAnswerRagQuestion() throws Exception {
        when(chatModel.generate(anyString()))
            .thenReturn("根据公司差旅政策，一线城市住宿费每日上限为500元。");

        RagQuestionDTO dto = new RagQuestionDTO();
        dto.setQuestion("北京出差住宿费标准是多少？");

        postWithJwt("/ai/rag/ask", dto, 1L, "USER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.question").value("北京出差住宿费标准是多少？"))
            .andExpect(jsonPath("$.data.answer").isNotEmpty())
            .andExpect(jsonPath("$.data.model").value("deepseek-chat"));
    }
}
