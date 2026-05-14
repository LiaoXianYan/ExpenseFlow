package com.expenseflow.ai.feign.fallback;

import com.expenseflow.ai.feign.ExpenseFeignClient;
import com.expenseflow.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExpenseFeignFallbackFactory implements FallbackFactory<ExpenseFeignClient> {

    @Override
    public ExpenseFeignClient create(Throwable cause) {
        log.error("expense-service 调用失败: {}", cause.getMessage());
        return (invoiceId, ocrStatus, invoiceNo, amount) -> {
            log.warn("OCR 结果回写失败: invoiceId={}", invoiceId);
            return Result.fail("OCR 结果回写失败");
        };
    }
}
