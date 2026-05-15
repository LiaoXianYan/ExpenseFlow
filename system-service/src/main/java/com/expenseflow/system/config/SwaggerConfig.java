package com.expenseflow.system.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ExpenseFlow System Service")
                .description("ExpenseFlow 差旅报销智能管理平台 — 系统服务 (用户/角色/权限/部门/租户)")
                .version("1.0.0"));
    }
}
