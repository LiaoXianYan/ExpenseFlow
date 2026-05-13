package com.expenseflow.expense.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "expense.ocr")
public class OcrConfig {
    private boolean mock = true;
    private String uploadPath = "./upload/invoice";
    private String accessKeyId = "";
    private String accessKeySecret = "";
}
