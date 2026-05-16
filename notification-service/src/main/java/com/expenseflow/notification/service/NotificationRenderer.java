package com.expenseflow.notification.service;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class NotificationRenderer {

    /**
     * 替换模板中的 {varName} 占位符
     * 未匹配的占位符保留原样
     */
    public String render(String template, Map<String, Object> data) {
        if (template == null || data == null) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue().toString());
            }
        }
        return result;
    }
}
