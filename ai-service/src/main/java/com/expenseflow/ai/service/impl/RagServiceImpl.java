package com.expenseflow.ai.service.impl;

import com.expenseflow.ai.dto.RagQuestionDTO;
import com.expenseflow.ai.service.RagService;
import com.expenseflow.ai.vo.RagAnswerVO;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private final ChatLanguageModel chatModel;

    private static final String POLICY_CONTEXT = """
        差旅费用政策参考:
        1. 交通费: 高铁二等座、飞机经济舱为报销标准，超标需总监审批
        2. 住宿费: 一线城市(北上广深)每日上限500元，其他城市每日上限350元
        3. 餐费: 每日补助100元，无需发票
        4. 出差申请: 金额>5000元需总监审批
        5. 报销单: 需关联已审批的出差申请单
        6. 发票: 增值税专用发票可抵扣税款，普通发票不可抵扣
        7. 审批流程: 出差申请→经理审批(→总监审批>5000), 报销单→财务审核→经理审批
        """;

    @Override
    public RagAnswerVO ask(RagQuestionDTO dto) {
        String prompt = POLICY_CONTEXT + "\n用户问题: " + dto.getQuestion()
            + "\n请基于上述政策回答，简洁明了。";

        String answer;
        try {
            answer = chatModel.generate(prompt);
            log.debug("RAG 问答完成: question={}", dto.getQuestion());
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败，使用默认回答", e);
            answer = "抱歉，AI 服务暂时不可用，请稍后重试。您也可以查阅公司差旅费用政策手册。";
        }

        RagAnswerVO vo = new RagAnswerVO();
        vo.setQuestion(dto.getQuestion());
        vo.setAnswer(answer);
        vo.setModel("deepseek-chat");
        return vo;
    }
}
