package com.expenseflow.ai.service;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.vo.RagAnswerVO;

public interface RagService {
    RagAnswerVO ask(RagQuestionDTO dto);
}
