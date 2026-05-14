package com.expenseflow.ai.config;

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
                .title("ExpenseFlow AI Service")
                .description("ExpenseFlow 差旅报销智能管理平台 — AI 智能服务 (OCR/审单/RAG)")
                .version("1.0.0"));
    }
}
