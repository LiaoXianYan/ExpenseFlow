package com.expenseflow.ai.controller;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.service.RagService;
import com.expenseflow.ai.vo.RagAnswerVO;
import com.expenseflow.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @PostMapping("/ask")
    public Result<RagAnswerVO> ask(@Valid @RequestBody RagQuestionDTO dto) {
        return Result.ok(ragService.ask(dto));
    }
}
