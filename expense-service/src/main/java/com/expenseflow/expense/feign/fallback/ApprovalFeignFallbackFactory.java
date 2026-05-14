package com.expenseflow.expense.feign.fallback;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.dto.ApprovalStartDTO;
import com.expenseflow.expense.feign.ApprovalFeignClient;
import com.expenseflow.expense.feign.dto.ApprovalProcessStartResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
public class ApprovalFeignFallbackFactory implements FallbackFactory<ApprovalFeignClient> {

    @Override
    public ApprovalFeignClient create(Throwable cause) {
        log.error("approval-service 调用失败, 使用降级: {}", cause.getMessage());
        return new ApprovalFeignClient() {
            @Override
            public Result<ApprovalProcessStartResponse> startApproval(ApprovalStartDTO dto) {
                ApprovalProcessStartResponse resp = new ApprovalProcessStartResponse();
                resp.setProcessInstanceId("fallback-pi-" + UUID.randomUUID().toString().substring(0, 12));
                resp.setApprovalLevel("SINGLE");
                resp.setWarnings(Collections.emptyList());
                return Result.ok(resp);
            }
        };
    }
}
