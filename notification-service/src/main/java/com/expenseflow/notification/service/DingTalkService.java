package com.expenseflow.notification.service;

import com.expenseflow.notification.config.DingTalkConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkService {

    private final DingTalkConfig dingTalkConfig;

    public void send(String title, String content) {
        if (dingTalkConfig.isMock()) {
            log.info("[钉钉 Mock] 发送消息: title={}, content={}", title, content);
            return;
        }
        // 真实 webhook 调用
        log.info("[钉钉] 发送消息到 webhook: {}", dingTalkConfig.getWebhookUrl());
    }
}
