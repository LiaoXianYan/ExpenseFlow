package com.expenseflow.expense.feign.fallback;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Slf4j
@Component
public class ApprovalFeignFallbackFactory implements FallbackFactory<ApprovalFeignClient> {

    @Override
    public ApprovalFeignClient create(Throwable cause) {
        log.info("approval-service 未就绪, 使用 Mock 审批: {}", cause.getMessage());
        return new ApprovalFeignClient() {
            @Override
            public Result<String> startApproval(ApprovalStartDTO dto) {
                return Result.ok("mock-pi-" + UUID.randomUUID().toString().substring(0, 12));
            }
        };
    }
}
