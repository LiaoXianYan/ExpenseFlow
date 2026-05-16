package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsRuleServiceTest {

    private final DroolsRuleService ruleService = new DroolsRuleService(null); // Java fallback

    // ============ amount-threshold (5 tests) ============

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

    // ============ type-compliance (3 tests) ============

    @Test
    @DisplayName("费用类型不在政策范围内 → BLOCK")
    void unmatchedExpenseTypeShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setItems(List.of(item("OTHER", 100)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "TYPE_MISMATCH".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("单笔超政策上限 → BLOCK")
    void itemExceedsPolicyMaxShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(6000);
        input.setItems(List.of(item("TRANSPORT", 6000)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "POLICY_VIOLATION".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("同类型同日超日限额 → WARN")
    void dailyLimitExceededShouldWarn() {
        LocalDate today = LocalDate.now();
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(700);
        input.setItems(List.of(
            item("MEAL", 60, today),
            item("MEAL", 60, today)));
        input.setPolicies(List.of(policy("MEAL", 200, 100)));

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "POLICY_VIOLATION".equals(v.getType()) && "WARN".equals(v.getSeverity())
                && v.getMessage().contains("日限额"));
    }

    // ============ duplicate-detection (3 tests) ============

    @Test
    @DisplayName("发票号已存在 → BLOCK")
    void duplicateInvoiceNoShouldBlock() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setInvoices(List.of(invoice("INV001", 1000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setUsedInvoiceNos(List.of("INV001"));
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "DUPLICATE".equals(v.getType()) && "BLOCK".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("三要素重复标志 → WARN")
    void amountDateVendorMatchShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        ApplicantHistory history = new ApplicantHistory();
        history.setHasAmountDateVendorMatch(true);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "DUPLICATE".equals(v.getType()) && "WARN".equals(v.getSeverity()));
    }

    @Test
    @DisplayName("发票号不在历史中 → 不触发重复")
    void newInvoiceNoShouldNotTriggerDuplicate() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1000);
        input.setInvoices(List.of(invoice("INV_NEW", 1000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setUsedInvoiceNos(List.of("INV001", "INV002"));
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).noneMatch(v -> "DUPLICATE".equals(v.getType()));
    }

    // ============ anomaly-detection (3 tests) ============

    @Test
    @DisplayName("金额远超历史均值 → WARN")
    void amountDeviatesFromAverageShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(5000);
        input.setItems(List.of(item("HOTEL", 5000)));
        ApplicantHistory history = new ApplicantHistory();
        history.setAvgAmount(500);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && "WARN".equals(v.getSeverity())
                && v.getMessage().contains("远超历史均值"));
    }

    @Test
    @DisplayName("近30天 ≥5笔 → WARN")
    void highFrequencyShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(2000);
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(6);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && v.getMessage().contains("频率偏高"));
    }

    @Test
    @DisplayName("疑似拆单标志 → WARN")
    void suspectedSplitShouldWarn() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(500);
        ApplicantHistory history = new ApplicantHistory();
        history.setSuspectedSplit(true);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).anyMatch(v ->
            "ANOMALY".equals(v.getType()) && v.getMessage().contains("拆单"));
    }

    // ============ 组合场景 (2 tests) ============

    @Test
    @DisplayName("多违规同时触发")
    void multipleViolationsTriggeredTogether() {
        LocalDate today = LocalDate.now();
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(2000);
        input.setItems(List.of(
            item("OTHER", 500),
            item("MEAL", 60, today),
            item("MEAL", 50, today)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0), policy("MEAL", 200, 100)));
        input.setInvoices(List.of(invoice("INV_DUP", 500)));
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(6);
        history.setAvgAmount(100);
        history.setUsedInvoiceNos(List.of("INV_DUP"));
        history.setSuspectedSplit(true);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("合规报销无违规")
    void compliantReportNoViolations() {
        RuleInput input = new RuleInput();
        input.setBusinessType("EXPENSE_REPORT");
        input.setAmount(1500);
        input.setItems(List.of(item("TRANSPORT", 1500)));
        input.setPolicies(List.of(policy("TRANSPORT", 5000, 0)));
        ApplicantHistory history = new ApplicantHistory();
        history.setRecentReportCount(1);
        history.setAvgAmount(1500);
        input.setHistory(history);

        RuleOutput result = ruleService.evaluate(input);

        assertThat(result.getViolations()).isEmpty();
    }

    // ============ helper methods ============

    private ExpenseItemInput item(String type, double amount) {
        ExpenseItemInput i = new ExpenseItemInput();
        i.setExpenseType(type);
        i.setAmount(amount);
        i.setExpenseDate(LocalDate.now());
        return i;
    }

    private ExpenseItemInput item(String type, double amount, LocalDate date) {
        ExpenseItemInput i = item(type, amount);
        i.setExpenseDate(date);
        return i;
    }

    private PolicyInput policy(String type, double max, double daily) {
        PolicyInput p = new PolicyInput();
        p.setExpenseType(type);
        p.setMaxAmount(max);
        p.setDailyLimit(daily);
        return p;
    }

    private InvoiceInput invoice(String no, double amount) {
        InvoiceInput i = new InvoiceInput();
        i.setInvoiceNo(no);
        i.setAmount(amount);
        return i;
    }
}
