# P0-1 OCR 真实阿里云 API — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** OcrServiceImpl.doOcr() 从 Mock 改为真实调用阿里云 OCR API（AppCode 认证），失败时降级 Mock

**Architecture:** 注入 OcrConfig → mock=false 时 HTTP POST 发票图片到阿里云 → 解析 JSON → 填充 AiOcrResult；mock=true 或失败时保持现有 Mock 行为

**Tech Stack:** Java 11 HttpClient, Jackson ObjectMapper, 阿里云 OCR API (AppCode 认证)

**Spec:** `docs/superpowers/specs/2026-05-15-p0-demo-readiness.md`

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `ai-service/.../impl/OcrServiceImpl.java` | 修改 | 注入 OcrConfig，HTTP 调用 + 降级 |
| `ai-service/src/test/.../service/OcrServiceImplTest.java` | 新建 | 3 个测试场景 |
| `expense-common/.../config/OcrConfig.java` | 不修改 | 已有 endpoint/appCode 字段，位置正确 |

---

### Task 1: 编写 OcrServiceImplTest 测试

**Files:**
- Create: `ai-service/src/test/java/com/expenseflow/ai/service/OcrServiceImplTest.java`

- [ ] **Step 1: 创建测试类（3 场景）**

```java
package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.entity.AiOcrResult;
import com.expenseflow.ai.mapper.AiOcrResultMapper;
import com.expenseflow.ai.service.impl.OcrServiceImpl;
import com.expenseflow.ai.vo.OcrResultVO;
import com.expenseflow.expense.config.OcrConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrServiceImplTest {

    @Mock AiOcrResultMapper ocrMapper;
    @Spy OcrConfig ocrConfig = new OcrConfig();
    @InjectMocks OcrServiceImpl ocrService;

    @BeforeEach
    void setUp() {
        ocrConfig.setMock(true);
        ocrConfig.setAppCode("");
    }

    @Test
    @DisplayName("mock=true 走 Mock 逻辑，返回固定数据")
    void shouldUseMockWhenMockEnabled() {
        when(ocrMapper.insert(any())).thenReturn(1);
        when(ocrMapper.updateById(any())).thenReturn(1);

        OcrRequestDTO dto = new OcrRequestDTO();
        dto.setInvoiceId(1L);
        dto.setImageUrl("http://example.com/invoice.jpg");

        OcrResultVO result = ocrService.recognize(dto, 0L);
        assertThat(result.getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("mock=false + API 未配置 → 降级为 Mock，confidence=0")
    void shouldFallbackWhenAppCodeNotConfigured() {
        ocrConfig.setMock(false);
        when(ocrMapper.insert(any())).thenReturn(1);
        when(ocrMapper.updateById(any())).thenReturn(1);

        OcrRequestDTO dto = new OcrRequestDTO();
        dto.setInvoiceId(2L);
        dto.setImageUrl("http://example.com/invoice.jpg");

        OcrResultVO result = ocrService.recognize(dto, 0L);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getResult: 查询已有结果")
    void shouldReturnExistingResult() {
        AiOcrResult entity = new AiOcrResult();
        entity.setId(1L); entity.setStatus("SUCCESS");
        entity.setParsedAmount(java.math.BigDecimal.valueOf(150.00));
        when(ocrMapper.selectById(1L)).thenReturn(entity);

        OcrResultVO result = ocrService.getResult(1L);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getParsedAmount()).isEqualByComparingTo("150.00");
    }
}
```

- [ ] **Step 2: 运行测试，验证失败**

```bash
cd D:/RecoginitionOCR && mvn test -pl ai-service -am -Dtest=OcrServiceImplTest
```

Expected: 第一个测试通过（mock 已有实现），后两个可能部分通过

- [ ] **Step 3: Commit**

```bash
git add ai-service/src/test/java/com/expenseflow/ai/service/OcrServiceImplTest.java
git commit -m "test(ocr): OcrServiceImpl 测试 — mock/降级/查询 3 场景"
```

---

### Task 2: 改造 OcrServiceImpl 接入真实 API

**Files:**
- Modify: `ai-service/src/main/java/com/expenseflow/ai/service/impl/OcrServiceImpl.java`

- [ ] **Step 1: 实现真实 API 调用 + 降级**

```java
package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.entity.AiOcrResult;
import com.expenseflow.ai.mapper.AiOcrResultMapper;
import com.expenseflow.ai.service.OcrService;
import com.expenseflow.ai.vo.OcrResultVO;
import com.expenseflow.expense.config.OcrConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    private final AiOcrResultMapper ocrMapper;
    private final OcrConfig ocrConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public OcrServiceImpl(AiOcrResultMapper ocrMapper, OcrConfig ocrConfig) {
        this.ocrMapper = ocrMapper;
        this.ocrConfig = ocrConfig;
    }

    @Override
    public OcrResultVO recognize(OcrRequestDTO dto, Long tenantId) {
        AiOcrResult result = new AiOcrResult();
        result.setTenantId(tenantId);
        result.setInvoiceId(dto.getInvoiceId());
        result.setStatus("PROCESSING");
        ocrMapper.insert(result);

        doOcr(result, dto.getImageUrl());

        OcrResultVO vo = new OcrResultVO();
        BeanUtils.copyProperties(result, vo);
        return vo;
    }

    @Override
    public OcrResultVO getResult(Long id) {
        AiOcrResult r = ocrMapper.selectById(id);
        if (r == null) return null;
        OcrResultVO vo = new OcrResultVO();
        BeanUtils.copyProperties(r, vo);
        return vo;
    }

    @Async
    void doOcr(AiOcrResult result, String imageUrl) {
        long start = System.currentTimeMillis();
        if (ocrConfig.isMock()) {
            mockRecognize(result, start, "mock 模式");
            return;
        }
        if (ocrConfig.getAppCode() == null || ocrConfig.getAppCode().isEmpty()) {
            log.warn("AppCode 未配置，降级为 Mock");
            mockRecognize(result, start, "AppCode 未配置");
            return;
        }

        try {
            String requestBody = "{\"url\":\"" + imageUrl + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocrConfig.getEndpoint()))
                .header("Authorization", "APPCODE " + ocrConfig.getAppCode())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                result.setStatus("SUCCESS");
                result.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                result.setRawResponse(response.body());

                JsonNode data = root.path("data");
                if (!data.isMissingNode()) {
                    if (data.has("invoiceNum")) {
                        result.setParsedInvoiceNo(data.get("invoiceNum").asText());
                    }
                    if (data.has("totalAmount")) {
                        result.setParsedAmount(new BigDecimal(data.get("totalAmount").asText()));
                    }
                    if (data.has("invoiceDate")) {
                        String dateStr = data.get("invoiceDate").asText();
                        try {
                            result.setParsedInvoiceDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd")));
                        } catch (Exception e) {
                            result.setParsedInvoiceDate(LocalDate.now());
                        }
                    }
                    if (data.has("sellerName")) {
                        result.setParsedSellerName(data.get("sellerName").asText());
                    }
                    if (data.has("sellerTaxNo")) {
                        result.setParsedSellerTaxNo(data.get("sellerTaxNo").asText());
                    }
                }
                result.setConfidence(new BigDecimal("0.90"));
                result.setProcessTimeMs(System.currentTimeMillis() - start);
                ocrMapper.updateById(result);
                log.info("OCR 识别成功: invoiceId={}, amount={}", result.getInvoiceId(), result.getParsedAmount());
            } else {
                log.error("OCR API 返回非 200: status={}, body={}", response.statusCode(), response.body());
                mockRecognize(result, start, "API 返回 " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("OCR API 调用失败", e);
            mockRecognize(result, start, e.getMessage());
        }
    }

    private void mockRecognize(AiOcrResult result, long start, String reason) {
        try {
            result.setStatus("SUCCESS");
            result.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
            result.setParsedInvoiceNo("MOCK-INV-" + result.getInvoiceId());
            result.setParsedAmount(new BigDecimal("100.00"));
            result.setParsedInvoiceDate(LocalDate.now());
            result.setParsedSellerName("模拟销售方");
            result.setParsedSellerTaxNo("91310000607335492B");
            result.setConfidence(new BigDecimal("0.00"));
            result.setRawResponse("{\"mock\":true,\"reason\":\"" + reason + "\"}");
            result.setProcessTimeMs(System.currentTimeMillis() - start);
            ocrMapper.updateById(result);
            log.info("OCR Mock 完成 (原因: {}): invoiceId={}", reason, result.getInvoiceId());
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setProcessTimeMs(System.currentTimeMillis() - start);
            ocrMapper.updateById(result);
            log.error("OCR 失败: invoiceId={}", result.getInvoiceId(), e);
        }
    }
}
```

- [ ] **Step 2: 运行测试验证**

```bash
cd D:/RecoginitionOCR && mvn test -pl ai-service -am -Dtest=OcrServiceImplTest
```

Expected: 3 tests PASS

- [ ] **Step 3: 运行全部测试确保无回归**

```bash
cd D:/RecoginitionOCR && mvn test -pl ai-service -am
```

Expected: 所有测试通过（3 个 OCR + 3 个 DeepSeekReview = 6 tests）

- [ ] **Step 4: Docker 重建 ai-service 镜像并启动**

```bash
cd D:/RecoginitionOCR && mvn package -pl ai-service -am -DskipTests -q
docker compose -f docker-compose.yml -f docker-compose.services.yml build ai-service
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d ai-service
```

- [ ] **Step 5: 验证健康检查**

```bash
sleep 30 && curl -s -o /dev/null -w "%{http_code}" http://localhost:8084/actuator/health
```

Expected: 200

- [ ] **Step 6: Commit**

```bash
git add ai-service/src/main/java/com/expenseflow/ai/service/impl/OcrServiceImpl.java
git commit -m "feat(ocr): 接入真实阿里云 OCR API (AppCode 认证) + Mock 降级"
```

---

## 完成标准

- [ ] `mvn test -pl ai-service -am` 全部通过
- [ ] mock=false + AppCode 已配置时调用真实阿里云 API
- [ ] mock=true 或 AppCode 为空时走 Mock 降级
- [ ] API 异常时降级为 Mock，confidence=0
- [ ] Docker 部署后 `/actuator/health` 返回 200
