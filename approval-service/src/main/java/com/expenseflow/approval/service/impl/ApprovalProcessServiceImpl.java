package com.expenseflow.approval.service.impl;

import com.expenseflow.approval.dto.*;
import com.expenseflow.approval.feign.ExpenseFeignClient;
import com.expenseflow.approval.feign.dto.ApplicantHistoryDTO;
import com.expenseflow.approval.feign.dto.ExpenseItemDTO;
import com.expenseflow.approval.feign.dto.ExpensePolicyDTO;
import com.expenseflow.approval.feign.dto.InvoiceDTO;
import com.expenseflow.approval.service.ApprovalProcessService;
import com.expenseflow.approval.service.DroolsRuleService;
import com.expenseflow.common.exception.BusinessException;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    private final RuntimeService runtimeService;
    private final DroolsRuleService droolsRuleService;
    private final ExpenseFeignClient expenseFeignClient;

    @Override
    @SentinelResource(value = "approval_process_start", fallback = "startProcessFallback")
    public ProcessStartResponse startProcess(ApprovalStartDTO dto) {
        // 1. 组装 RuleInput
        RuleInput ruleInput = new RuleInput();
        ruleInput.setBusinessType(dto.getBusinessType());
        ruleInput.setAmount(dto.getAmount() != null ? dto.getAmount().doubleValue() : 0);

        // 2. 查询费用项 + 发票
        if ("EXPENSE_REPORT".equals(dto.getBusinessType())) {
            try {
                var itemsResult = expenseFeignClient.getItemsByReportId(dto.getBusinessId());
                if (itemsResult != null && itemsResult.getData() != null) {
                    List<ExpenseItemInput> itemInputs = itemsResult.getData().stream().map(i -> {
                        ExpenseItemInput ei = new ExpenseItemInput();
                        ei.setExpenseType(i.getExpenseType());
                        ei.setAmount(i.getAmount() != null ? i.getAmount().doubleValue() : 0);
                        ei.setExpenseDate(i.getExpenseDate());
                        return ei;
                    }).toList();
                    ruleInput.setItems(itemInputs);
                }

                var invoicesResult = expenseFeignClient.getInvoicesByReportId(dto.getBusinessId());
                if (invoicesResult != null && invoicesResult.getData() != null) {
                    List<InvoiceInput> invoiceInputs = invoicesResult.getData().stream().map(i -> {
                        InvoiceInput ii = new InvoiceInput();
                        ii.setInvoiceNo(i.getInvoiceNo());
                        ii.setInvoiceCode(i.getInvoiceCode());
                        ii.setInvoiceDate(i.getInvoiceDate());
                        ii.setAmount(i.getAmount() != null ? i.getAmount().doubleValue() : 0);
                        ii.setSellerName(i.getSellerName());
                        return ii;
                    }).toList();
                    ruleInput.setInvoices(invoiceInputs);
                }
            } catch (Exception e) {
                log.warn("查询费用项/发票失败，跳过类型合规检查: {}", e.getMessage());
            }

            // 3. 查询申请人历史
            try {
                var historyResult = expenseFeignClient.getApplicantHistory(dto.getApplicantId());
                if (historyResult != null && historyResult.getData() != null) {
                    var h = historyResult.getData();
                    ApplicantHistory history = new ApplicantHistory();
                    history.setApplicantId(h.getApplicantId());
                    history.setRecentReportCount(h.getRecentReportCount());
                    history.setAvgAmount(h.getAvgAmount());
                    history.setUsedInvoiceNos(h.getUsedInvoiceNos() != null ? h.getUsedInvoiceNos() : List.of());
                    history.setUsedCostRecordIds(h.getUsedCostRecordIds() != null ? h.getUsedCostRecordIds() : List.of());
                    history.setHasAmountDateVendorMatch(h.isHasAmountDateVendorMatch());
                    history.setSuspectedSplit(h.isSuspectedSplit());
                    ruleInput.setHistory(history);
                }
            } catch (Exception e) {
                log.warn("查询历史失败，跳过重复/异常检测: {}", e.getMessage());
            }

            // 4. 查询费用政策
            try {
                var policyResult = expenseFeignClient.getPolicies();
                if (policyResult != null && policyResult.getData() != null) {
                    List<PolicyInput> policies = policyResult.getData().stream()
                        .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                        .map(p -> {
                            PolicyInput pi = new PolicyInput();
                            pi.setExpenseType(p.getExpenseType());
                            pi.setMaxAmount(p.getMaxAmount() != null ? p.getMaxAmount().doubleValue() : 0);
                            pi.setDailyLimit(p.getDailyLimit() != null ? p.getDailyLimit().doubleValue() : 0);
                            pi.setCityTier(p.getCityTier());
                            return pi;
                        }).toList();
                    ruleInput.setPolicies(policies);
                }
            } catch (Exception e) {
                log.warn("查询费用政策失败，跳过合规检查: {}", e.getMessage());
            }
        }

        // 5. 执行规则
        RuleOutput rule = droolsRuleService.evaluate(ruleInput);

        // 6. BLOCK 级违规直接拒绝
        long blockCount = rule.getViolations().stream()
            .filter(v -> "BLOCK".equals(v.getSeverity())).count();
        if (blockCount > 0) {
            String msg = rule.getViolations().stream()
                .filter(v -> "BLOCK".equals(v.getSeverity()))
                .map(RuleOutput.Violation::getMessage)
                .collect(java.util.stream.Collectors.joining("; "));
            log.warn("审批启动被规则拦截: {}", msg);
            throw new BusinessException("审批启动失败: " + msg);
        }

        // 7. 流程变量 + 启动流程
        Map<String, Object> variables = new HashMap<>();
        variables.put("businessType", dto.getBusinessType());
        variables.put("businessId", dto.getBusinessId());
        variables.put("requestNo", dto.getRequestNo());
        variables.put("applicantId", dto.getApplicantId());
        variables.put("applicantName", dto.getApplicantName() != null ? dto.getApplicantName() : "未知");
        variables.put("amount", dto.getAmount() != null ? dto.getAmount().doubleValue() : 0);
        variables.put("needDirector", rule.isNeedDirector());
        variables.put("departmentId", dto.getDepartmentId());
        variables.put("violations", rule.getViolations());

        String processDefKey = switch (dto.getBusinessType()) {
            case "TRAVEL_REQUEST" -> "travel-request-approval";
            case "EXPENSE_REPORT" -> "expense-report-approval";
            default -> throw new IllegalArgumentException("未知业务类型: " + dto.getBusinessType());
        };

        var pi = runtimeService.startProcessInstanceByKey(processDefKey, variables);
        log.info("流程启动: processInstanceId={}, businessType={}, needDirector={}, violations={}",
            pi.getId(), dto.getBusinessType(), rule.isNeedDirector(), rule.getViolations().size());

        String approvalLevel = rule.isNeedDirector() ? "DUAL" : "SINGLE";
        return new ProcessStartResponse(pi.getId(), approvalLevel, rule.getWarnings());
    }

    public ProcessStartResponse startProcessFallback(ApprovalStartDTO dto, Throwable t) {
        log.warn("审批流程启动触发 Sentinel 降级: businessType={}, error={}",
            dto.getBusinessType(), t.getMessage());
        return new ProcessStartResponse("fallback-" + java.util.UUID.randomUUID(),
            "SINGLE", java.util.List.of("审批服务繁忙"));
    }
}
