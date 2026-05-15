package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.entity.AiOcrResult;
import com.expenseflow.ai.mapper.AiOcrResultMapper;
import com.expenseflow.ai.service.OcrService;
import com.expenseflow.ai.vo.OcrResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrServiceImpl implements OcrService {

    private final AiOcrResultMapper ocrMapper;

    @Override
    public OcrResultVO recognize(OcrRequestDTO dto, Long tenantId) {
        AiOcrResult result = new AiOcrResult();
        result.setTenantId(tenantId);
        result.setInvoiceId(dto.getInvoiceId());
        result.setStatus("PROCESSING");
        ocrMapper.insert(result);

        doOcr(result);

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
    void doOcr(AiOcrResult result) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(800);

            result.setStatus("SUCCESS");
            result.setRequestId(UUID.randomUUID().toString().substring(0, 32));
            result.setParsedInvoiceNo("MOCK-INV-" + result.getInvoiceId());
            result.setParsedAmount(new BigDecimal("100.00"));
            result.setParsedInvoiceDate(LocalDate.now());
            result.setParsedSellerName("模拟销售方");
            result.setParsedSellerTaxNo("91310000607335492B");
            result.setConfidence(new BigDecimal("0.95"));
            result.setRawResponse("{\"mock\":true,\"invoiceId\":" + result.getInvoiceId() + "}");
            result.setProcessTimeMs(System.currentTimeMillis() - start);
            ocrMapper.updateById(result);
            log.info("OCR 识别完成: invoiceId={}, confidence=0.95", result.getInvoiceId());
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setProcessTimeMs(System.currentTimeMillis() - start);
            ocrMapper.updateById(result);
            log.error("OCR 识别失败: invoiceId={}", result.getInvoiceId(), e);
        }
    }
}
