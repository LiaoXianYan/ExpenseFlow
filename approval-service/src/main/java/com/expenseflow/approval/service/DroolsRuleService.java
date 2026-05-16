package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.RuleInput;
import com.expenseflow.approval.dto.RuleOutput;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class DroolsRuleService {

    private final KieContainer kieContainer;

    public DroolsRuleService(@Autowired(required = false) KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public RuleOutput evaluate(String businessType, BigDecimal amount) {
        RuleInput input = new RuleInput();
        input.setBusinessType(businessType);
        input.setAmount(amount != null ? amount.doubleValue() : 0);
        RuleOutput output = new RuleOutput();

        if (kieContainer != null) {
            KieSession session = kieContainer.newKieSession();
            try {
                session.insert(input);
                session.insert(output);
                session.fireAllRules();
                log.debug("Drools 引擎评估完成: needDirector={}, warnings={}",
                    output.isNeedDirector(), output.getWarnings());
            } finally {
                session.dispose();
            }
        } else {
            // Java fallback: 规则等价于 DRL
            if ("TRAVEL_REQUEST".equals(businessType) && input.getAmount() > 5000) {
                output.setNeedDirector(true);
            }
            if ("EXPENSE_REPORT".equals(businessType) && input.getAmount() > 10000) {
                output.getWarnings().add("报销金额较大，需重点关注");
            }
            if ("EXPENSE_REPORT".equals(businessType) && input.getAmount() > 20000) {
                output.getWarnings().add("报销金额超过20000，建议总监复核");
            }
            log.debug("Java 规则引擎评估完成: needDirector={}, warnings={}",
                output.isNeedDirector(), output.getWarnings());
        }

        return output;
    }
}
