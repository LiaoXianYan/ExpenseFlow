package com.expenseflow.approval.feign;

import com.expenseflow.approval.dto.ApprovalCallbackDTO;
import com.expenseflow.approval.feign.fallback.ExpenseFeignFallbackFactory;
import com.expenseflow.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "expense-service", path = "/expense",
             fallbackFactory = ExpenseFeignFallbackFactory.class)
public interface ExpenseFeignClient {

    @PutMapping("/callback/approval-result")
    Result<Void> updateApprovalResult(@RequestBody ApprovalCallbackDTO dto);
}
