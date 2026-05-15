package com.expenseflow.approval.service;

import com.expenseflow.approval.dto.ApprovalStartDTO;
import com.expenseflow.approval.dto.ProcessStartResponse;
import com.expenseflow.approval.dto.RuleOutput;
import com.expenseflow.approval.service.impl.ApprovalProcessServiceImpl;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalProcessServiceImplTest {

    @Mock RuntimeService runtimeService;
    @Mock DroolsRuleService droolsRuleService;
    @Mock ProcessInstance processInstance;
    @InjectMocks ApprovalProcessServiceImpl processService;

    @Test
    @DisplayName("启动出差申请审批流程")
    void shouldStartTravelApprovalProcess() {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBusinessType("TRAVEL_REQUEST"); dto.setBusinessId(1L);
        dto.setRequestNo("TR-001"); dto.setApplicantId(1L);
        dto.setAmount(BigDecimal.valueOf(3000));

        RuleOutput rule = new RuleOutput();
        rule.setNeedDirector(false);
        when(droolsRuleService.evaluate(anyString(), any())).thenReturn(rule);
        when(processInstance.getId()).thenReturn("pi-123");
        when(runtimeService.startProcessInstanceByKey(eq("travel-request-approval"), anyMap()))
            .thenReturn(processInstance);

        ProcessStartResponse result = processService.startProcess(dto);
        assertThat(result.getProcessInstanceId()).isEqualTo("pi-123");
        assertThat(result.getApprovalLevel()).isEqualTo("SINGLE");
    }

    @Test
    @DisplayName("启动报销单审批流程 (需要总监)")
    void shouldStartExpenseApprovalWithDirector() {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBusinessType("EXPENSE_REPORT"); dto.setBusinessId(2L);
        dto.setRequestNo("ER-001"); dto.setApplicantId(1L);
        dto.setAmount(BigDecimal.valueOf(15000));

        RuleOutput rule = new RuleOutput();
        rule.setNeedDirector(true);
        rule.getWarnings().add("高额警告");
        when(droolsRuleService.evaluate(anyString(), any())).thenReturn(rule);
        when(processInstance.getId()).thenReturn("pi-456");
        when(runtimeService.startProcessInstanceByKey(eq("expense-report-approval"), anyMap()))
            .thenReturn(processInstance);

        ProcessStartResponse result = processService.startProcess(dto);
        assertThat(result.getProcessInstanceId()).isEqualTo("pi-456");
        assertThat(result.getApprovalLevel()).isEqualTo("DUAL");
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    @DisplayName("未知业务类型抛异常")
    void shouldThrowForUnknownBusinessType() {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBusinessType("UNKNOWN"); dto.setBusinessId(1L);
        dto.setAmount(BigDecimal.valueOf(1000));

        RuleOutput rule = new RuleOutput();
        when(droolsRuleService.evaluate(anyString(), any())).thenReturn(rule);

        assertThrows(IllegalArgumentException.class, () -> processService.startProcess(dto));
    }
}
