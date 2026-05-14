package com.expenseflow.ai.feign;

import com.expenseflow.ai.feign.fallback.ExpenseFeignFallbackFactory;
import com.expenseflow.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "expense-service", path = "/expense",
             fallbackFactory = ExpenseFeignFallbackFactory.class)
public interface ExpenseFeignClient {

    @PutMapping("/callback/ocr-result")
    Result<Void> updateOcrResult(@RequestParam Long invoiceId,
                                  @RequestParam String ocrStatus,
                                  @RequestParam(required = false) String invoiceNo,
                                  @RequestParam(required = false) java.math.BigDecimal amount);
}
