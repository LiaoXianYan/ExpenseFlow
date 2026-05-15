package com.expenseflow.ai.feign.fallback;

import com.expenseflow.ai.feign.SystemFeignClient;
import com.expenseflow.ai.feign.dto.SystemUserDTO;
import com.expenseflow.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemFeignFallbackFactory implements FallbackFactory<SystemFeignClient> {

    @Override
    public SystemFeignClient create(Throwable cause) {
        log.error("system-service 调用失败: {}", cause.getMessage());
        return id -> {
            SystemUserDTO dto = new SystemUserDTO();
            dto.setId(id);
            dto.setRealName("未知用户");
            return Result.ok(dto);
        };
    }
}
