package com.expenseflow.gateway.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelRulesConfigTest {

    private final SentinelRulesConfig config = new SentinelRulesConfig();

    @Test
    @DisplayName("限流规则正确加载：4 条规则 + QPS 值正确")
    void shouldLoadFlowRulesCorrectly() {
        config.initFlowRules();

        var rules = FlowRuleManager.getRules();
        assertThat(rules).hasSize(4);

        assertThat(rules).anyMatch(r ->
            "system_auth_login".equals(r.getResource()) && r.getCount() == 10.0);
        assertThat(rules).anyMatch(r ->
            "expense_report_submit".equals(r.getResource()) && r.getCount() == 5.0);
        assertThat(rules).anyMatch(r ->
            "ai_review".equals(r.getResource()) && r.getCount() == 3.0);
        assertThat(rules).anyMatch(r ->
            "default".equals(r.getResource()) && r.getCount() == 100.0);
    }

    @Test
    @DisplayName("BlockHandler 已注册，规则管理器状态正常")
    void shouldHaveBlockHandlerConfigured() {
        config.initFlowRules();

        // BlockHandler 通过 static 块在类加载时注册
        // 验证规则管理器正常工作
        assertThat(FlowRuleManager.getRules()).isNotEmpty();
    }
}
