package com.expenseflow.approval.feign.fallback;

import com.expenseflow.approval.feign.ExpenseFeignClient;
import com.expenseflow.approval.feign.dto.ExpenseItemDTO;
import com.expenseflow.approval.feign.dto.InvoiceDTO;
import com.expenseflow.approval.feign.dto.ApplicantHistoryDTO;
import com.expenseflow.approval.feign.dto.ExpensePolicyDTO;
import com.expenseflow.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ExpenseFeignFallbackFactory implements FallbackFactory<ExpenseFeignClient> {

    @Override
    public ExpenseFeignClient create(Throwable cause) {
        log.error("ExpenseFeignClient 调用失败", cause);
        return new ExpenseFeignClient() {
            @Override
            public Result<Void> updateApprovalResult(com.expenseflow.approval.dto.ApprovalCallbackDTO dto) {
                return Result.fail(500, "expense-service 不可用");
            }

            @Override
            public Result<List<ExpenseItemDTO>> getItemsByReportId(Long id) {
                return Result.fail(500, "expense-service 不可用");
            }

            @Override
            public Result<List<InvoiceDTO>> getInvoicesByReportId(Long id) {
                return Result.fail(500, "expense-service 不可用");
            }

            @Override
            public Result<ApplicantHistoryDTO> getApplicantHistory(Long applicantId) {
                return Result.fail(500, "expense-service 不可用");
            }

            @Override
            public Result<List<ExpensePolicyDTO>> getPolicies() {
                return Result.fail(500, "expense-service 不可用");
            }
        };
    }
}
