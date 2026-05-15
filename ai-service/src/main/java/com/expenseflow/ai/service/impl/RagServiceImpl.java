package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.service.RagService;
import com.expenseflow.ai.vo.RagAnswerVO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private static final String FALLBACK_CONTEXT = """
        差旅费用政策参考:
        1. 交通费: 高铁二等座、飞机经济舱为报销标准，超标需总监审批
        2. 住宿费: 一线城市(北上广深)每日上限500元，其他城市每日上限350元
        3. 餐费: 每日补助100元，无需发票
        4. 出差申请: 金额>5000元需总监审批
        5. 报销单: 需关联已审批的出差申请单
        6. 审批流程: 出差申请→经理审批(→总监审批>5000), 报销单→财务审核→经理审批
        """;

    public RagServiceImpl(ChatLanguageModel chatModel,
                          EmbeddingModel embeddingModel,
                          EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    @PostConstruct
    void init() {
        try {
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:/policies/*.md");

            int totalChunks = 0;
            for (Resource resource : resources) {
                String content = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

                Metadata metadata = new Metadata();
                metadata.add("filename", resource.getFilename());
                Document doc = Document.from(content, metadata);
                List<TextSegment> segments = splitter.split(doc);
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                embeddingStore.addAll(embeddings, segments);
                totalChunks += segments.size();
            }
            log.info("RAG 向量索引初始化完成: {} 个文档, {} 个 chunk",
                resources.length, totalChunks);
        } catch (Exception e) {
            log.error("RAG 索引初始化失败，将使用降级模式", e);
        }
    }

    @Override
    public RagAnswerVO ask(RagQuestionDTO dto) {
        String context;
        try {
            Embedding questionEmbedding = embeddingModel.embed(dto.getQuestion()).content();
            var searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(3)
                    .minScore(0.5)
                    .build());

            context = searchResult.matches().stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n---\n"));

            if (context.isEmpty()) {
                context = FALLBACK_CONTEXT;
            }
        } catch (Exception e) {
            log.error("向量检索失败，使用降级上下文", e);
            context = FALLBACK_CONTEXT;
        }

        String prompt = "你是一个企业差旅报销政策专家。根据以下政策内容回答用户问题。\n\n"
            + "政策内容：\n" + context + "\n\n"
            + "用户问题：" + dto.getQuestion() + "\n\n"
            + "请基于上述政策回答，简洁明了。如果政策中没有明确说明，请据实告知。";

        String answer;
        try {
            answer = chatModel.generate(prompt);
            log.debug("RAG 问答完成: question={}, contextLength={}",
                dto.getQuestion(), context.length());
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            answer = "抱歉，AI 服务暂时不可用，请稍后重试。您也可以查阅公司差旅费用政策手册。";
        }

        RagAnswerVO vo = new RagAnswerVO();
        vo.setQuestion(dto.getQuestion());
        vo.setAnswer(answer);
        vo.setModel("deepseek-chat");
        return vo;
    }
}
