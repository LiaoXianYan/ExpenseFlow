package com.expenseflow.approval.feign;

import com.expenseflow.approval.dto.ApprovalCallbackDTO;
import com.expenseflow.approval.feign.dto.ApplicantHistoryDTO;
import com.expenseflow.approval.feign.dto.ExpenseItemDTO;
import com.expenseflow.approval.feign.dto.ExpensePolicyDTO;
import com.expenseflow.approval.feign.dto.InvoiceDTO;
import com.expenseflow.approval.feign.fallback.ExpenseFeignFallbackFactory;
import com.expenseflow.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "expense-service", path = "/expense",
             fallbackFactory = ExpenseFeignFallbackFactory.class)
public interface ExpenseFeignClient {

    @PutMapping("/callback/approval-result")
    Result<Void> updateApprovalResult(@RequestBody ApprovalCallbackDTO dto);

    @GetMapping("/report/{id}/items")
    Result<List<ExpenseItemDTO>> getItemsByReportId(@PathVariable Long id);

    @GetMapping("/report/{id}/invoices")
    Result<List<InvoiceDTO>> getInvoicesByReportId(@PathVariable Long id);

    @GetMapping("/report/applicant/{applicantId}/history")
    Result<ApplicantHistoryDTO> getApplicantHistory(@PathVariable Long applicantId);

    @GetMapping("/policy/list")
    Result<List<ExpensePolicyDTO>> getPolicies();
}
