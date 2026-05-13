package com.expenseflow.expense.feign;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.feign.fallback.ApprovalFeignFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "approval-service", path = "/approval",
             fallbackFactory = ApprovalFeignFallbackFactory.class)
public interface ApprovalFeignClient {

    @PostMapping("/process/start")
    Result<String> startApproval(@RequestBody ApprovalStartDTO dto);
}
