package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.RuleOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsRuleServiceTest {

    private final DroolsRuleService ruleService = new DroolsRuleService(null); // Java fallback mode

    @Test
    @DisplayName("TRAVEL_REQUEST 金额 > 5000 需要总监审批")
    void travelRequestAbove5000NeedsDirector() {
        RuleOutput result = ruleService.evaluate("TRAVEL_REQUEST", BigDecimal.valueOf(6000));
        assertThat(result.isNeedDirector()).isTrue();
    }

    @Test
    @DisplayName("TRAVEL_REQUEST 金额 ≤ 5000 不需要总监审批")
    void travelRequestBelow5000NoDirector() {
        RuleOutput result = ruleService.evaluate("TRAVEL_REQUEST", BigDecimal.valueOf(3000));
        assertThat(result.isNeedDirector()).isFalse();
    }

    @Test
    @DisplayName("EXPENSE_REPORT 金额 > 10000 触发高额警告")
    void expenseReportAbove10000Warns() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(15000));
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("EXPENSE_REPORT 金额 > 20000 触发总监复核警告")
    void expenseReportAbove20000DirectorWarning() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(25000));
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("20000"));
    }

    @Test
    @DisplayName("小金额无警告")
    void smallAmountNoWarnings() {
        RuleOutput result = ruleService.evaluate("EXPENSE_REPORT", BigDecimal.valueOf(5000));
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.isNeedDirector()).isFalse();
    }
}
