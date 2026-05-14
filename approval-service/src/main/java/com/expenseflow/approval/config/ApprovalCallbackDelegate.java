package com.expenseflow.approval.config;

import com.expenseflow.approval.dto.ApprovalCallbackDTO;
import com.expenseflow.approval.feign.ExpenseFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component("approvalCallbackDelegate")
public class ApprovalCallbackDelegate implements ExecutionListener {

    private final ExpenseFeignClient expenseFeignClient;

    public ApprovalCallbackDelegate(ExpenseFeignClient expenseFeignClient) {
        this.expenseFeignClient = expenseFeignClient;
    }

    @Override
    public void notify(DelegateExecution execution) {
        String businessType = (String) execution.getVariable("businessType");
        Long businessId = (Long) execution.getVariable("businessId");
        String outcome = (String) execution.getVariable("outcome");

        log.info("流程结束回调: businessType={}, businessId={}, outcome={}",
            businessType, businessId, outcome);

        ApprovalCallbackDTO callback = new ApprovalCallbackDTO();
        callback.setBusinessType(businessType);
        callback.setBusinessId(businessId);
        callback.setProcessInstanceId(execution.getProcessInstanceId());
        callback.setOutcome(outcome != null ? outcome : "APPROVED");

        try {
            expenseFeignClient.updateApprovalResult(callback);
            log.info("审批结果回写成功: businessId={}, outcome={}", businessId, callback.getOutcome());
        } catch (Exception e) {
            log.error("审批结果回写失败: businessId={}, error={}", businessId, e.getMessage());
        }
    }
}
