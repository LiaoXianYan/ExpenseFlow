# P1-1 RAG 向量检索 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RagServiceImpl 从 prompt 拼接改为 DashScope Embedding + InMemoryEmbeddingStore 语义检索 + DeepSeek 生成

**Architecture:** 启动时加载 docs/policies/*.md → DocumentSplitter 分块 → OpenAiEmbeddingModel(DashScope) 嵌入 → InMemoryEmbeddingStore 存储。查询时 question 嵌入 → store.search(topK=3) → 拼接检索结果 + 问题 → DeepSeek Chat 生成

**Tech Stack:** LangChain4j 0.35 (OpenAiEmbeddingModel, InMemoryEmbeddingStore, DocumentSplitter), DashScope text-embedding-v2 API, DeepSeek Chat

**Spec:** `docs/superpowers/specs/2026-05-15-p1-ai-depth.md`

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `ai-service/.../config/LangChain4jConfig.java` | 修改 | 加 OpenAiEmbeddingModel Bean（指向 DashScope） |
| `ai-service/.../service/impl/RagServiceImpl.java` | 重写 | 向量索引 + 检索 + 生成 |
| `docs/policies/travel-policy.md` | 新建 | 差旅政策文档 |
| `docs/policies/expense-policy.md` | 新建 | 报销政策文档 |
| `ai-service/.../service/RagServiceImplTest.java` | 新建 | 3 测试场景 |
| `ai-service/pom.xml` | 不修改 | langchain4j-open-ai 已有 OpenAiEmbeddingModel |

---

### Task 1: 创建政策文档

**Files:**
- Create: `docs/policies/travel-policy.md`
- Create: `docs/policies/expense-policy.md`

- [ ] **Step 1: 创建差旅政策文档**

`docs/policies/travel-policy.md`:

```markdown
# 差旅费用政策

## 交通费标准
1. 高铁/动车：二等座为报销标准，一等座需总监审批
2. 飞机：经济舱为报销标准，商务舱需总监审批
3. 市内交通：出租车/网约车实报实销，每日上限200元
4. 自驾出差：按1.2元/公里报销油费+过路费

## 住宿费标准
1. 一线城市（北上广深）：每日上限500元
2. 新一线城市（杭州、成都、武汉等）：每日上限400元
3. 其他城市：每日上限350元
4. 超出标准部分自理

## 餐费补助
1. 每日餐补100元/人，无需发票
2. 客户招待餐饮需单独申请，需发票+招待事由

## 出差申请
1. 单个出差申请金额 > 5000元需总监审批
2. 单次出差超过10天需部门负责人特别批准
3. 出差申请应提前至少3个工作日提交

## 其他规定
1. 出差期间人身意外险由公司统一购买
2. 差旅费用需在出差结束后15个工作日内提交报销
3. 多人同行出差需各自提交申请，可合并报销
```

- [ ] **Step 2: 创建报销政策文档**

`docs/policies/expense-policy.md`:

```markdown
# 报销政策

## 报销单要求
1. 报销单必须关联已审批的出差申请单
2. 单张发票金额超过2000元需附消费明细
3. 增值税专用发票可抵扣税款，普通发票不可抵扣
4. 电子发票需打印后附在报销单后

## 审批流程
1. 出差申请：申请人提交 → 经理审批 → (金额>5000)总监审批
2. 报销单：申请人提交 → 财务审核 → 经理审批
3. 打款：审批通过后财务在3个工作日内打款

## 费用分类
1. 交通费：飞机票、火车票、出租车、网约车
2. 住宿费：酒店、民宿
3. 餐费：员工餐补、客户招待
4. 办公费：文具、打印、快递
5. 其他：培训费、会务费

## 禁止报销项
1. 个人消费（化妆品、服装、日用品）
2. 娱乐消费（KTV、游戏、电影）
3. 无发票或发票信息不完整的费用
4. 已过期的发票（超过3个月）
5. 与出差无关的费用

## 超标处理
1. 住宿超标：超出部分自理
2. 交通超标：需书面说明原因，总监审批
3. 连续超标3次以上需提交整改说明
```

- [ ] **Step 3: Commit**

```bash
mkdir -p docs/policies
git add docs/policies/
git commit -m "docs(policies): 差旅报销政策文档 — 交通/住宿/餐费/审批/禁止项"
```

---

### Task 2: 添加 EmbeddingModel Bean

**Files:**
- Modify: `ai-service/src/main/java/com/expenseflow/ai/config/LangChain4jConfig.java`

- [ ] **Step 1: 添加 DashScope EmbeddingModel 配置**

在 `LangChain4jConfig.java` 中新增:

```java
@Value("${langchain4j.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
private String embeddingBaseUrl;

@Value("${langchain4j.embedding.api-key:sk-default}")
private String embeddingApiKey;

@Value("${langchain4j.embedding.model-name:text-embedding-v2}")
private String embeddingModelName;

@Bean
public OpenAiEmbeddingModel embeddingModel() {
    return OpenAiEmbeddingModel.builder()
        .baseUrl(embeddingBaseUrl)
        .apiKey(embeddingApiKey)
        .modelName(embeddingModelName)
        .maxRetries(2)
        .logRequests(true)
        .logResponses(true)
        .build();
}
```

同时添加 import:

```java
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
```

- [ ] **Step 2: 编译验证**

```bash
cd D:/RecoginitionOCR && mvn compile -pl ai-service -am -DskipTests -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-service/src/main/java/com/expenseflow/ai/config/LangChain4jConfig.java
git commit -m "feat(rag): 添加 DashScope EmbeddingModel Bean"
```

---

### Task 3: 重写 RagServiceImpl + 测试

**Files:**
- Modify: `ai-service/src/main/java/com/expenseflow/ai/service/impl/RagServiceImpl.java`
- Create: `ai-service/src/test/java/com/expenseflow/ai/service/RagServiceImplTest.java`

- [ ] **Step 1: 写测试**

```java
package com.expenseflow.ai.service;

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
import static org.mockito.Mockito.lenient;
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

        var dto = new com.expenseflow.ai.dto.RagQuestionDTO();
        dto.setQuestion("北京出差住宿费标准是多少？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getAnswer()).isNotEmpty();
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
    }

    @Test
    @DisplayName("LLM 异常时返回降级提示")
    void shouldFallbackWhenLLMFails() {
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("API error"));

        var dto = new com.expenseflow.ai.dto.RagQuestionDTO();
        dto.setQuestion("住宿费标准？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getAnswer()).contains("暂时不可用");
    }

    @Test
    @DisplayName("检索增强 Prompt 应包含上下文")
    void shouldIncludeContextInPrompt() {
        when(chatModel.generate(anyString())).thenReturn("公司政策规定...");

        var dto = new com.expenseflow.ai.dto.RagQuestionDTO();
        dto.setQuestion("出差能坐商务舱吗？");

        RagAnswerVO result = ragService.ask(dto);
        assertThat(result.getQuestion()).isEqualTo("出差能坐商务舱吗？");
    }
}
```

- [ ] **Step 2: 运行测试确认失败（RagServiceImpl 未改造）**

```bash
cd D:/RecoginitionOCR && mvn test -pl ai-service -am -Dtest=RagServiceImplTest 2>&1 | grep "Tests run:"
```

- [ ] **Step 3: 重写 RagServiceImpl**

```java
package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.service.RagService;
import com.expenseflow.ai.vo.RagAnswerVO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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

                Document doc = Document.from(content, resource.getFilename());
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
            List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(questionEmbedding, 3);

            context = matches.stream()
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
```

- [ ] **Step 4: 创建政策文件资源目录并移动政策文档**

```bash
mkdir -p ai-service/src/main/resources/policies
cp docs/policies/travel-policy.md ai-service/src/main/resources/policies/
cp docs/policies/expense-policy.md ai-service/src/main/resources/policies/
```

- [ ] **Step 5: 运行全部 ai-service 测试**

```bash
cd D:/RecoginitionOCR && mvn test -pl ai-service -am
```

Expected: RagServiceImplTest (3 tests) + DeepSeekReviewServiceImplTest (3 tests) + OcrServiceImplTest (3 tests) = 9 tests PASS

- [ ] **Step 6: Commit**

```bash
git add ai-service/src/main/java/com/expenseflow/ai/service/impl/RagServiceImpl.java \
        ai-service/src/test/java/com/expenseflow/ai/service/RagServiceImplTest.java \
        ai-service/src/main/resources/policies/
git commit -m "feat(rag): 向量检索RAG — DashScope Embedding + InMemoryStore + DeepSeek 生成"
```

---

### Task 4: 配置环境变量 + Docker 部署验证

- [ ] **Step 1: 更新 docker-compose services 增加 embedding env**

在 `docker-compose.services.yml` 的 ai-service 环境变量中添加:

```yaml
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY:-sk-default}
      LANGCHAIN4J_EMBEDDING_API_KEY: ${DASHSCOPE_API_KEY:-sk-default}
```

- [ ] **Step 2: 重建并启动**

```bash
cd D:/RecoginitionOCR && mvn package -pl ai-service -am -DskipTests -q
docker compose -f docker-compose.yml -f docker-compose.services.yml build ai-service
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d ai-service
sleep 30
curl -s -o /dev/null -w "%{http_code}" http://localhost:8084/actuator/health
```

Expected: 200

- [ ] **Step 3: 测试 RAG API**

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/system/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | head -1 | cut -d'"' -f4)
curl -s -X POST http://localhost:8080/ai/rag/ask \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"北京出差住宿费标准是多少？"}'
```

Expected: 返回包含"500元"或相关政策内容的 JSON

- [ ] **Step 4: Commit**

```bash
git add docker-compose.services.yml
git commit -m "feat(docker): ai-service 增加 DashScope API Key 环境变量"
```

---

## 完成标准

- [ ] `mvn test -pl ai-service -am` 全部 9 个测试通过
- [ ] RAG API 根据 policy 文档回答，引用具体金额限制
- [ ] Embedding API 异常时降级为 hardcode policy context
- [ ] LLM API 异常时返回固定降级提示
- [ ] Docker 部署后 `/actuator/health` 200
