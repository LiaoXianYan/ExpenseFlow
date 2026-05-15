package com.expenseflow.notification.feign;

import com.expenseflow.notification.feign.dto.SystemUserDTO;
import com.expenseflow.notification.feign.fallback.SystemFeignFallbackFactory;
import com.expenseflow.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "system-service", path = "/system",
             fallbackFactory = SystemFeignFallbackFactory.class)
public interface SystemFeignClient {

    @GetMapping("/user/{id}")
    Result<SystemUserDTO> getUser(@PathVariable Long id);
}
