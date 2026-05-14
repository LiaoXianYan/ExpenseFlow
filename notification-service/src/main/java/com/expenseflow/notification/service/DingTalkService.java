package com.expenseflow.notification.service;

import com.expenseflow.notification.config.DingTalkConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DingTalkService {

    private final DingTalkConfig dingTalkConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String title, String content) {
        if (dingTalkConfig.isMock()) {
            log.info("[钉钉 Mock] 发送消息: title={}, content={}", title, content);
            return;
        }

        try {
            String text = "## " + title + "\n\n" + content;
            String body = objectMapper.writeValueAsString(Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("title", title, "text", text)
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dingTalkConfig.getWebhookUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("[钉钉] 消息发送成功: title={}", title);
            } else {
                log.error("[钉钉] 消息发送失败: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[钉钉] 消息发送异常: {}", e.getMessage());
        }
    }
}
