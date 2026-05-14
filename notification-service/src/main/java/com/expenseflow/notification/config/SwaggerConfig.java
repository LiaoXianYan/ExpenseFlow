package com.expenseflow.notification.config;

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
                .title("ExpenseFlow Notification Service")
                .description("ExpenseFlow 差旅报销智能管理平台 — 通知服务 (站内消息/钉钉机器人)")
                .version("1.0.0"));
    }
}
