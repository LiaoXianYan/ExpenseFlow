package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.config.OcrConfig;
import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.entity.AiOcrResult;
import com.expenseflow.ai.mapper.AiOcrResultMapper;
import com.expenseflow.ai.service.OcrService;
import com.expenseflow.ai.vo.OcrResultVO;
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
                            result.setParsedInvoiceDate(LocalDate.parse(dateStr,
                                DateTimeFormatter.ofPattern("yyyyMMdd")));
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
                log.info("OCR 识别成功: invoiceId={}, amount={}",
                    result.getInvoiceId(), result.getParsedAmount());
            } else {
                log.error("OCR API 返回非 200: status={}, body={}",
                    response.statusCode(), response.body());
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
