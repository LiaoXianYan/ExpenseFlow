package com.expenseflow.approval.feign.fallback;

import com.expenseflow.approval.dto.ApprovalCallbackDTO;
import com.expenseflow.approval.feign.ExpenseFeignClient;
import com.expenseflow.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExpenseFeignFallbackFactory implements FallbackFactory<ExpenseFeignClient> {

    @Override
    public ExpenseFeignClient create(Throwable cause) {
        log.error("expense-service 回调失败: {}", cause.getMessage());
        return dto -> {
            log.warn("审批结果回写失败, businessId={}, outcome={}", dto.getBusinessId(), dto.getOutcome());
            return Result.fail("审批结果回写失败");
        };
    }
}
