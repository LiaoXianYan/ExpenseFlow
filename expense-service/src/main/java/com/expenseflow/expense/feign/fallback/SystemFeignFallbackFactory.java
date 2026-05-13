package com.expenseflow.expense.feign.fallback;

import com.expenseflow.common.result.Result;
import com.expenseflow.expense.feign.SystemFeignClient;
import com.expenseflow.expense.feign.dto.SystemDeptDTO;
import com.expenseflow.expense.feign.dto.SystemUserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemFeignFallbackFactory implements FallbackFactory<SystemFeignClient> {

    @Override
    public SystemFeignClient create(Throwable cause) {
        log.error("system-service 调用失败: {}", cause.getMessage());
        return new SystemFeignClient() {
            @Override
            public Result<SystemUserDTO> getUser(Long id) {
                SystemUserDTO dto = new SystemUserDTO();
                dto.setId(id); dto.setRealName("未知用户");
                return Result.ok(dto);
            }
            @Override
            public Result<SystemDeptDTO> getDepartment(Long id) {
                SystemDeptDTO dto = new SystemDeptDTO();
                dto.setId(id); dto.setDeptName("未知部门");
                return Result.ok(dto);
            }
        };
    }
}
