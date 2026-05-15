package com.expenseflow.approval.config;

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
                .title("ExpenseFlow Approval Service")
                .description("ExpenseFlow 差旅报销智能管理平台 — 审批引擎服务 (Flowable + Drools)")
                .version("1.0.0"));
    }
}
