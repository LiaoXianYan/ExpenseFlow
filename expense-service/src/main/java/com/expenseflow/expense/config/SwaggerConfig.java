package com.expenseflow.expense.config;

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
                .title("ExpenseFlow Expense Service")
                .description("ExpenseFlow 差旅报销智能管理平台 — 报销服务 (出差申请/报销/发票/消费/打款)")
                .version("1.0.0"));
    }
}
