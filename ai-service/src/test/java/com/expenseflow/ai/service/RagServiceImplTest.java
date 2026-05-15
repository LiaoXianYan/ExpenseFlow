package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.service.impl.RagServiceImpl;
import com.expenseflow.ai.vo.RagAnswerVO;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock ChatLanguageModel chatModel;
    EmbeddingStore<TextSegment> embeddingStore;
    EmbeddingModel embeddingModel;
    RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        embeddingStore = new InMemoryEmbeddingStore<>();
        embeddingModel = OpenAiEmbeddingModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey("test-key")
            .modelName("text-embedding-v2")
            .maxRetries(0)
            .build();
        ragService = new RagServiceImpl(chatModel, embeddingModel, embeddingStore);
    }

    @Test
    @DisplayName("问答应返回非空回答")
    void shouldAnswerQuestion() {
        when(chatModel.generate(anyString())).thenReturn("根据公司政策，住宿费一线城市每日上限为500元。");

        RagQuestionDTO dto = new RagQuestionDTO();
        dto.setQuestion("北京出差住宿费标准是多少？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getAnswer()).isNotEmpty();
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    @DisplayName("LLM 异常时返回降级提示")
    void shouldFallbackWhenLLMFails() {
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("API error"));

        RagQuestionDTO dto = new RagQuestionDTO();
        dto.setQuestion("住宿费标准？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getAnswer()).contains("暂时不可用");
    }

    @Test
    @DisplayName("检索增强回答，question 字段正确回传")
    void shouldEchoQuestion() {
        when(chatModel.generate(anyString())).thenReturn("公司政策规定...");

        RagQuestionDTO dto = new RagQuestionDTO();
        dto.setQuestion("出差能坐商务舱吗？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getQuestion()).isEqualTo("出差能坐商务舱吗？");
    }
}
