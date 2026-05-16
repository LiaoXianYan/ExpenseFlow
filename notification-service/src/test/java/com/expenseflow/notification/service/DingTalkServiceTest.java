package com.expenseflow.notification.service;

import com.expenseflow.notification.config.DingTalkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DingTalkServiceTest {

    @Mock DingTalkConfig dingTalkConfig;
    @InjectMocks DingTalkService dingTalkService;

    @Test
    @DisplayName("mock 模式仅输出日志")
    void shouldOnlyLogInMockMode() {
        when(dingTalkConfig.isMock()).thenReturn(true);

        dingTalkService.send("测试标题", "测试内容");
        // 无实际 HTTP 调用，验证不会抛异常
    }

    @Test
    @DisplayName("webhook URL 为空时跳过发送")
    void shouldSkipWhenWebhookUrlEmpty() {
        when(dingTalkConfig.isMock()).thenReturn(false);
        when(dingTalkConfig.getWebhookUrl()).thenReturn(null);

        dingTalkService.send("标题", "内容");
        // 不应该发送 HTTP 请求
    }

    @Test
    @DisplayName("非 mock 模式且有 webhook URL 时不抛未处理异常")
    void shouldNotThrowWhenRealSendFails() {
        when(dingTalkConfig.isMock()).thenReturn(false);
        when(dingTalkConfig.getWebhookUrl())
            .thenReturn("https://oapi.dingtalk.com/robot/send?access_token=test");
        when(dingTalkConfig.getSecret()).thenReturn("");

        // 真实 HTTP 发送会因 URL 无效而失败，但不应抛未处理异常
        dingTalkService.send("测试", "内容");
        // 无异常 = pass
    }
}
