package com.expenseflow.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRendererTest {

    private final NotificationRenderer renderer = new NotificationRenderer();

    @Test
    @DisplayName("正常替换占位符")
    void shouldReplacePlaceholders() {
        String result = renderer.render("{name}了{action}", Map.of("name", "张三", "action", "提交"));
        assertThat(result).isEqualTo("张三了提交");
    }

    @Test
    @DisplayName("未匹配占位符保留原样")
    void shouldKeepUnmatchedPlaceholders() {
        String result = renderer.render("{name}提交了", Map.of());
        assertThat(result).isEqualTo("{name}提交了");
    }

    @Test
    @DisplayName("null 模板返回 null")
    void shouldReturnNullForNullTemplate() {
        String result = renderer.render(null, Map.of("a", "b"));
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("null data 返回原模板")
    void shouldReturnTemplateForNullData() {
        String result = renderer.render("{name}提交了", null);
        assertThat(result).isEqualTo("{name}提交了");
    }

    @Test
    @DisplayName("多行模板替换")
    void shouldReplaceMultipleLines() {
        String template = "**申请人：** {name}\n**金额：** {amount} 元";
        String result = renderer.render(template, Map.of("name", "张三", "amount", "5000"));
        assertThat(result).contains("张三").contains("5000");
    }
}
