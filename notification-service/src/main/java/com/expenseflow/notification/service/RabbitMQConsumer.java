package com.expenseflow.notification.service;

import com.expenseflow.notification.service.DingTalkService;
import com.expenseflow.notification.service.MessageService;
import com.expenseflow.notification.service.NotificationRenderer;
import com.expenseflow.notification.service.TemplateService;
import com.expenseflow.notification.vo.TemplateVO;
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
    private final TemplateService templateService;
    private final NotificationRenderer renderer;
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

        log.info("收到通知事件: eventId={}, eventType={}", eventId, map.get("eventType"));

        try {
            String eventType = (String) map.getOrDefault("eventType", "unknown");
            Long businessId = toLong(map.get("businessId"));
            Long tenantId = toLong(map.getOrDefault("tenantId", 0));
            Long userId = toLong(map.getOrDefault("applicantId", map.get("userId")));
            if (userId == null) userId = 1L;

            // 事件 → 模板 code
            String templateCode = mapEventToTemplate(eventType);
            String title;
            String content;

            if (templateCode != null) {
                TemplateVO tpl = templateService.getByCode(templateCode);
                if (tpl != null) {
                    title = renderer.render(tpl.getTitleTemplate(), map);
                    content = renderer.render(tpl.getContentTemplate(), map);
                } else {
                    log.warn("模板 {} 不存在，使用默认文案", templateCode);
                    title = "系统通知";
                    content = (String) map.getOrDefault("message", "您有一条新的通知");
                }
            } else {
                title = "系统通知";
                content = (String) map.getOrDefault("message", "您有一条新的通知");
            }

            // 站内消息
            messageService.send(userId, title, content, "NOTIFICATION",
                (String) map.get("businessType"), businessId, tenantId);

            // 钉钉推送
            dingTalkService.send(title, content);

        } catch (Exception e) {
            log.error("通知事件处理失败: eventId={}", eventId, e);
        }
    }

    private String mapEventToTemplate(String eventType) {
        return switch (eventType) {
            case "EXPENSE_SUBMITTED" -> "DING_REPORT_SUBMITTED";
            case "APPROVAL_RESULT" -> "DING_APPROVAL_RESULT";
            case "PAYMENT_COMPLETED" -> "DING_PAYMENT_COMPLETED";
            case "REPORT_WITHDRAWN" -> "DING_REPORT_WITHDRAWN";
            default -> null;
        };
    }

    private Long toLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) return Long.valueOf((String) obj);
        return null;
    }
}
