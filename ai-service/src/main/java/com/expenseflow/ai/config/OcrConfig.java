package com.expenseflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "expense.ocr")
public class OcrConfig {
    private boolean mock = true;
    private String endpoint = "https://ocrapi-advanced.aliyuncs.com/rest/160601/ocr/ocr_invoice.json";
    private String appCode = "";
}
