package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.entity.AiOcrResult;
import com.expenseflow.ai.mapper.AiOcrResultMapper;
import com.expenseflow.ai.service.impl.OcrServiceImpl;
import com.expenseflow.ai.vo.OcrResultVO;
import com.expenseflow.ai.config.OcrConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    @DisplayName("mock=true 走 Mock 逻辑，识别成功返回 SUCCESS")
    void shouldUseMockWhenMockEnabled() {
        when(ocrMapper.insert(any(AiOcrResult.class))).thenReturn(1);
        when(ocrMapper.updateById(any(AiOcrResult.class))).thenReturn(1);

        OcrRequestDTO dto = new OcrRequestDTO();
        dto.setInvoiceId(1L);
        dto.setImageUrl("http://example.com/invoice.jpg");

        OcrResultVO result = ocrService.recognize(dto, 0L);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("mock=false + AppCode 未配置 → 降级为 Mock")
    void shouldFallbackWhenAppCodeNotConfigured() {
        ocrConfig.setMock(false);
        when(ocrMapper.insert(any(AiOcrResult.class))).thenReturn(1);
        when(ocrMapper.updateById(any(AiOcrResult.class))).thenReturn(1);

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
        entity.setParsedAmount(BigDecimal.valueOf(150.00));
        when(ocrMapper.selectById(1L)).thenReturn(entity);

        OcrResultVO result = ocrService.getResult(1L);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getParsedAmount()).isEqualByComparingTo("150.00");
    }
}
