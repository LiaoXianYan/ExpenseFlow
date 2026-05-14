package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.vo.OcrResultVO;

public interface OcrService {
    OcrResultVO recognize(OcrRequestDTO dto, Long tenantId);
    OcrResultVO getResult(Long id);
}
