package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.dto.RuleOutput.Violation;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DroolsRuleService {

    private final KieContainer kieContainer;

    public DroolsRuleService(@Autowired(required = false) KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    /**
     * @deprecated 保留旧方法向后兼容。新调用请使用 {@link #evaluate(RuleInput)}
     */
    @Deprecated
    public RuleOutput evaluate(String businessType, BigDecimal amount) {
        RuleInput input = new RuleInput();
        input.setBusinessType(businessType);
        input.setAmount(amount != null ? amount.doubleValue() : 0);
        return evaluate(input);
    }

    public RuleOutput evaluate(RuleInput input) {
        RuleOutput output = new RuleOutput();

        if (kieContainer != null) {
            KieSession session = kieContainer.newKieSession();
            try {
                session.insert(input);
                if (input.getItems() != null) {
                    input.getItems().forEach(session::insert);
                }
                if (input.getInvoices() != null) {
                    input.getInvoices().forEach(session::insert);
                }
                if (input.getPolicies() != null) {
                    input.getPolicies().forEach(session::insert);
                }
                if (input.getHistory() != null) {
                    session.insert(input.getHistory());
                }
                session.insert(output);
                session.fireAllRules();
                log.debug("Drools 评估完成: needDirector={}, violations={}",
                    output.isNeedDirector(), output.getViolations().size());
            } finally {
                session.dispose();
            }
        } else {
            javaFallback(input, output);
        }

        return output;
    }

    private void javaFallback(RuleInput input, RuleOutput output) {
        double amount = input.getAmount();

        // === amount-threshold ===
        if ("TRAVEL_REQUEST".equals(input.getBusinessType()) && amount > 5000) {
            output.setNeedDirector(true);
        }
        if ("EXPENSE_REPORT".equals(input.getBusinessType()) && amount > 10000) {
            output.getWarnings().add("报销金额较大，需重点关注");
        }
        if ("EXPENSE_REPORT".equals(input.getBusinessType()) && amount > 20000) {
            output.getWarnings().add("报销金额超过20000，建议总监复核");
        }

        // === type-compliance ===
        if (input.getPolicies() != null && input.getItems() != null) {
            Set<String> policyTypes = input.getPolicies().stream()
                .map(PolicyInput::getExpenseType).collect(Collectors.toSet());
            for (ExpenseItemInput item : input.getItems()) {
                if (!policyTypes.contains(item.getExpenseType())) {
                    output.getViolations().add(new Violation("TYPE_MISMATCH",
                        "费用类型 " + item.getExpenseType() + " 不在现行政策范围内", "BLOCK"));
                }
                for (PolicyInput p : input.getPolicies()) {
                    if (p.getExpenseType().equals(item.getExpenseType())) {
                        if (item.getAmount() > p.getMaxAmount()) {
                            output.getViolations().add(new Violation("POLICY_VIOLATION",
                                item.getExpenseType() + " 金额 " + item.getAmount()
                                    + " 超过政策上限 " + p.getMaxAmount(), "BLOCK"));
                        }
                    }
                }
            }

            // Daily limit check: sum amounts by type+date, compare against dailyLimit
            Map<String, Map<LocalDate, Double>> dailySums = new HashMap<>();
            for (ExpenseItemInput item : input.getItems()) {
                if (item.getExpenseDate() != null) {
                    dailySums
                        .computeIfAbsent(item.getExpenseType(), k -> new HashMap<>())
                        .merge(item.getExpenseDate(), item.getAmount(), Double::sum);
                }
            }
            for (PolicyInput p : input.getPolicies()) {
                Map<LocalDate, Double> dateMap = dailySums.get(p.getExpenseType());
                if (dateMap != null && p.getDailyLimit() > 0) {
                    for (Map.Entry<LocalDate, Double> e : dateMap.entrySet()) {
                        if (e.getValue() > p.getDailyLimit()) {
                            output.getViolations().add(new Violation("POLICY_VIOLATION",
                                p.getExpenseType() + " 同类型同日合计 " + e.getValue()
                                    + " 超过日限额 " + p.getDailyLimit(), "WARN"));
                        }
                    }
                }
            }
        }

        // === duplicate-detection ===
        if (input.getHistory() != null) {
            ApplicantHistory h = input.getHistory();
            if (input.getInvoices() != null) {
                for (InvoiceInput inv : input.getInvoices()) {
                    if (inv.getInvoiceNo() != null && !inv.getInvoiceNo().isEmpty()
                        && h.getUsedInvoiceNos().contains(inv.getInvoiceNo())) {
                        output.getViolations().add(new Violation("DUPLICATE",
                            "发票号 " + inv.getInvoiceNo() + " 已关联过其他报销单", "BLOCK"));
                    }
                }
            }
            if (h.isHasAmountDateVendorMatch()) {
                output.getViolations().add(new Violation("DUPLICATE",
                    "相同金额+日期+销方的发票已存在", "WARN"));
            }
        }

        // === anomaly-detection ===
        if (input.getHistory() != null) {
            ApplicantHistory h = input.getHistory();
            if (h.getAvgAmount() > 0 && input.getItems() != null) {
                for (ExpenseItemInput item : input.getItems()) {
                    if (item.getAmount() > h.getAvgAmount() * 3) {
                        output.getViolations().add(new Violation("ANOMALY",
                            item.getExpenseType() + " 金额 " + item.getAmount()
                                + " 远超历史均值 " + h.getAvgAmount(), "WARN"));
                    }
                }
            }
            if (h.getRecentReportCount() >= 5) {
                output.getViolations().add(new Violation("ANOMALY",
                    "近30天已有 " + h.getRecentReportCount() + " 笔报销，频率偏高", "WARN"));
            }
            if (h.isSuspectedSplit()) {
                output.getViolations().add(new Violation("ANOMALY",
                    "疑似拆单报销：高频低额", "WARN"));
            }
        }

        log.debug("Java fallback 评估完成: needDirector={}, violations={}",
            output.isNeedDirector(), output.getViolations().size());
    }
}
