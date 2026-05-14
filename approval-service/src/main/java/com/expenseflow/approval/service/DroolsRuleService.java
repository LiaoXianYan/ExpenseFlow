package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class DroolsRuleService {

    private final KieContainer kieContainer;

    public DroolsRuleService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RuleOutput evaluate(String businessType, BigDecimal amount) {
        RuleInput input = new RuleInput(businessType, amount != null ? amount.doubleValue() : 0);
        RuleOutput output = new RuleOutput();

        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(input);
            session.insert(output);
            session.fireAllRules();
            log.debug("Drools 评估完成: type={}, amount={}, needDirector={}, warnings={}",
                businessType, amount, output.isNeedDirector(), output.getWarnings());
        } finally {
            session.dispose();
        }
        return output;
    }
}
