package com.expenseflow.notification.service;

import com.expenseflow.notification.service.DingTalkService;
import com.expenseflow.notification.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQConsumer {

    private final MessageService messageService;
    private final DingTalkService dingTalkService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = "notification.event.queue")
    public void onNotificationEvent(Map<String, Object> map) {
        String eventId = (String) map.get("eventId");
        if (eventId == null) {
            log.warn("消息缺少 eventId，跳过处理");
            return;
        }
        Boolean firstTime = stringRedisTemplate.opsForValue()
            .setIfAbsent("event:consumed:" + eventId, "1", Duration.ofHours(24));
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("重复消息已忽略: eventId={}", eventId);
            return;
        }

        log.info("收到通知事件: eventId={}", eventId);
        try {
            String eventType = (String) map.getOrDefault("eventType", "unknown");
            String businessType = (String) map.get("businessType");
            Long businessId = toLong(map.get("businessId"));
            Long tenantId = toLong(map.getOrDefault("tenantId", 0));
            Long userId = toLong(map.getOrDefault("applicantId", map.get("userId")));

            if (userId == null) userId = 1L;

            String title;
            String content;
            if ("APPROVED".equals(map.get("outcome")) || "REJECTED".equals(map.get("outcome"))) {
                String resultText = "APPROVED".equals(map.get("outcome")) ? "通过" : "驳回";
                title = "审批结果通知";
                content = "您的" + businessType + "已" + resultText;
            } else {
                title = "系统通知";
                content = (String) map.getOrDefault("message", "您有一条新的通知");
            }

            messageService.send(userId, title, content, "NOTIFICATION",
                businessType, businessId, tenantId);
            dingTalkService.send(title, content);
        } catch (Exception e) {
            log.error("通知事件处理失败", e);
        }
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) return Long.valueOf((String) obj);
        return null;
    }
}
