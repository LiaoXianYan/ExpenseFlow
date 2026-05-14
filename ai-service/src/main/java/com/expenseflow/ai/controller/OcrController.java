package com.expenseflow.ai.controller;

import com.expenseflow.ai.BaseController;
import com.expenseflow.ai.dto.OcrRequestDTO;
import com.expenseflow.ai.service.OcrService;
import com.expenseflow.ai.vo.OcrResultVO;
import com.expenseflow.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/ocr")
@RequiredArgsConstructor
public class OcrController extends BaseController {

    private final OcrService ocrService;

    @PostMapping("/recognize")
    public Result<OcrResultVO> recognize(@Valid @RequestBody OcrRequestDTO dto) {
        return Result.ok(ocrService.recognize(dto, getCurrentTenantId()));
    }

    @GetMapping("/{id}")
    public Result<OcrResultVO> getResult(@PathVariable Long id) {
        return Result.ok(ocrService.getResult(id));
    }
}
