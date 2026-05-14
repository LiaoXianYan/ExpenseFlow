package com.expenseflow.approval.service.impl;

import com.expenseflow.approval.dto.ApprovalStartDTO;
import com.expenseflow.approval.dto.ProcessStartResponse;
import com.expenseflow.approval.dto.RuleOutput;
import com.expenseflow.approval.service.ApprovalProcessService;
import com.expenseflow.approval.service.DroolsRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalProcessServiceImpl implements ApprovalProcessService {

    private final RuntimeService runtimeService;
    private final DroolsRuleService droolsRuleService;

    @Override
    public ProcessStartResponse startProcess(ApprovalStartDTO dto) {
        RuleOutput rule = droolsRuleService.evaluate(dto.getBusinessType(), dto.getAmount());

        Map<String, Object> variables = new HashMap<>();
        variables.put("businessType", dto.getBusinessType());
        variables.put("businessId", dto.getBusinessId());
        variables.put("requestNo", dto.getRequestNo());
        variables.put("applicantId", dto.getApplicantId());
        variables.put("applicantName", dto.getApplicantName() != null ? dto.getApplicantName() : "未知");
        variables.put("amount", dto.getAmount() != null ? dto.getAmount().doubleValue() : 0);
        variables.put("needDirector", rule.isNeedDirector());
        variables.put("departmentId", dto.getDepartmentId());

        String processDefKey = switch (dto.getBusinessType()) {
            case "TRAVEL_REQUEST" -> "travel-request-approval";
            case "EXPENSE_REPORT" -> "expense-report-approval";
            default -> throw new IllegalArgumentException("未知业务类型: " + dto.getBusinessType());
        };

        var pi = runtimeService.startProcessInstanceByKey(processDefKey, variables);
        log.info("流程启动: processInstanceId={}, businessType={}, businessId={}, needDirector={}",
            pi.getId(), dto.getBusinessType(), dto.getBusinessId(), rule.isNeedDirector());

        String approvalLevel = rule.isNeedDirector() ? "DUAL" : "SINGLE";
        return new ProcessStartResponse(pi.getId(), approvalLevel, rule.getWarnings());
    }
}
