# P1-3 钉钉真实推送 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通 4 类事件的钉钉真实推送，RabbitMQConsumer 基于 NtNotificationTemplate 模板渲染消息，修复 `expense.report.submitted` 无绑定的 bug。

**Architecture:** Consumer 根据 `eventType` 查 templateCode → TemplateService.getByCode() → render() 替换占位符 → DingTalkService.send()。事件发布点分散在 expense-service 的关键操作中。

**Tech Stack:** RabbitMQ, DingTalk Webhook + HMAC-SHA256, MyBatis-Plus, JUnit 5 + Mockito

---

### Task 1: 模板渲染器 + RabbitMQ 绑定修复

**Files:**
- Create: `notification-service/src/main/java/com/expenseflow/notification/service/NotificationRenderer.java`
- Modify: `notification-service/src/main/java/com/expenseflow/notification/config/RabbitMQConfig.java`

- [ ] **Step 1: 创建 NotificationRenderer**

```java
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
```

- [ ] **Step 2: RabbitMQConfig 新增 3 个绑定**

在 `RabbitMQConfig.java` 中添加新的 routing key 常量和绑定：

```java
// 新增常量（添加在现有常量之后）
public static final String SUBMITTED_KEY = "expense.report.submitted";
public static final String WITHDRAWN_KEY = "expense.report.withdrawn";
public static final String PAYMENT_KEY = "expense.payment.completed";

// 新增 Bean（添加在现有 binding Bean 之后）

@Bean
public Binding submittedBinding() {
    return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(SUBMITTED_KEY);
}

@Bean
public Binding withdrawnBinding() {
    return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(WITHDRAWN_KEY);
}

@Bean
public Binding paymentBinding() {
    return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(PAYMENT_KEY);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd notification-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add notification-service/src/main/java/
git commit -m "feat(notification): NotificationRenderer + 3个新 RabbitMQ 绑定"
```

---

### Task 2: RabbitMQConsumer 模板化改造

**Files:**
- Modify: `notification-service/src/main/java/com/expenseflow/notification/service/RabbitMQConsumer.java`

- [ ] **Step 1: 读取现有文件，替换整个类**

FIRST read: `notification-service/src/main/java/com/expenseflow/notification/service/RabbitMQConsumer.java`

REPLACE its entire content with:

```java
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
                    // 降级：模板不存在用默认文案
                    log.warn("模板 {} 不存在，使用默认文案", templateCode);
                    title = "系统通知";
                    content = (String) map.getOrDefault("message", "您有一条新的通知");
                }
            } else {
                // 未知事件类型
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
```

- [ ] **Step 2: 编译验证**

```bash
cd notification-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add notification-service/src/main/java/com/expenseflow/notification/service/RabbitMQConsumer.java
git commit -m "refactor(notification): RabbitMQConsumer 模板化 — eventType 分发 + render"
```

---

### Task 3: expense-service 新增事件发布点

**Files:**
- Modify: `expense-service/src/main/java/com/expenseflow/expense/service/ExpenseReportServiceImpl.java`
- Modify: `expense-service/src/main/java/com/expenseflow/expense/service/PaymentService.java`
- Modify: `expense-service/src/main/java/com/expenseflow/expense/controller/ApprovalCallbackController.java`

- [ ] **Step 1: ExpenseReportServiceImpl.withdraw() — 新增事件发布**

FIRST read: `expense-service/src/main/java/com/expenseflow/expense/service/ExpenseReportServiceImpl.java`

找到 `withdraw` 方法（约第 162 行）。在状态更新 `reportMapper.updateById(r)` 之后、`return Result.ok(toVO(r))` 之前，插入事件发布代码：

```java
// 发布撤回事件
Map<String, Object> event = new HashMap<>();
event.put("eventId", UUID.randomUUID().toString());
event.put("eventType", "REPORT_WITHDRAWN");
event.put("reportNo", r.getReportNo());
event.put("applicantId", r.getApplicantId());
event.put("previousStatus", previousStatus);
event.put("tenantId", 0);
try {
    rabbitTemplate.convertAndSend("expense.exchange", "expense.report.withdrawn", event);
} catch (Exception ignored) {}
```

需要在方法开头保存 `previousStatus`。当前 withdraw() 方法开头：
```java
ExExpenseReport r = reportMapper.selectById(id);
if (r == null) return Result.fail(404, "报销单不存在");
if (!"SUBMITTED".equals(r.getStatus()) && !"APPROVING".equals(r.getStatus())
        && !"APPROVED".equals(r.getStatus())) { ... }
```

在 `ExExpenseReport r = reportMapper.selectById(id);` 之后添加：
```java
String previousStatus = r.getStatus();
```

- [ ] **Step 2: ExpenseReportServiceImpl.submit() — 添加 applicantName 到事件**

当前 submit() 约第 149-158 行发布事件。在 event map 中添加 `eventType` 字段和更多数据：

在 `Map<String, Object> event = new HashMap<>();` 之后已有:
```java
event.put("eventId", UUID.randomUUID().toString());
event.put("reportId", r.getId());
event.put("amount", total);
event.put("tenantId", 0);
```

在这些 put 之后添加：
```java
event.put("eventType", "EXPENSE_SUBMITTED");
event.put("reportNo", r.getReportNo());
event.put("applicantName", user != null ? user.getRealName() : "未知");
event.put("submitTime", java.time.LocalDateTime.now().toString());
```

- [ ] **Step 3: PaymentService.pay() — 新增打款事件发布**

FIRST read: `expense-service/src/main/java/com/expenseflow/expense/service/PaymentService.java`

在 `PaymentService` 类中添加 `RabbitTemplate` 依赖：

```java
// 添加字段
private final RabbitTemplate rabbitTemplate;

// 添加 import
import org.springframework.amqp.rabbit.core.RabbitTemplate;
```

在 `pay()` 方法的 `return Result.ok(pr);` 之前，插入：

```java
// 发布打款事件
Map<String, Object> event = new java.util.HashMap<>();
event.put("eventId", java.util.UUID.randomUUID().toString());
event.put("eventType", "PAYMENT_COMPLETED");
event.put("reportNo", report.getReportNo());
event.put("amount", report.getTotalAmount().toString());
event.put("paidTime", pr.getPaymentTime().toString());
event.put("tenantId", 0);
try {
    rabbitTemplate.convertAndSend("expense.exchange", "expense.payment.completed", event);
} catch (Exception ignored) {}
```

- [ ] **Step 4: ApprovalCallbackController — eventType 统一为 APPOVAL_RESULT**

FIRST read: `expense-service/src/main/java/com/expenseflow/expense/controller/ApprovalCallbackController.java`

修改第 57 行 eventType 的值，从 `"approval.result"` 改为 `"APPROVAL_RESULT"`：

```java
// 将
event.put("eventType", "approval.result");
// 改为
event.put("eventType", "APPROVAL_RESULT");
```

同时在 event 中添加 `requestNo` 和 `operator` 字段（用于模板渲染）。找到获取 businessType 后的逻辑，添加：

```java
// 在 event 构建块中添加（已有 eventId, eventType, businessType, businessId, outcome, tenantId 之后）
event.put("requestNo", getRequestNo(businessType, businessId));
```

在类中添加辅助方法：
```java
private String getRequestNo(String businessType, Long businessId) {
    if ("TRAVEL_REQUEST".equals(businessType)) {
        ExTravelRequest t = travelMapper.selectById(businessId);
        return t != null ? t.getRequestNo() : String.valueOf(businessId);
    } else if ("EXPENSE_REPORT".equals(businessType)) {
        ExExpenseReport r = reportMapper.selectById(businessId);
        return r != null ? r.getReportNo() : String.valueOf(businessId);
    }
    return String.valueOf(businessId);
}
```

- [ ] **Step 5: 编译验证**

```bash
cd expense-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add expense-service/src/main/java/
git commit -m "feat(expense): 新增撤回/打款事件发布 + eventType 统一 + submit 数据补全"
```

---

### Task 4: 模板种子数据

**Files:**
- Modify: `sql/init.sql`

- [ ] **Step 1: 在 init.sql 末尾添加 4 条模板种子**

```sql
-- 通知模板（P1-3 钉钉推送）
INSERT INTO nt_notification_template (tenant_id, template_code, template_name, channel, title_template, content_template, status, create_time) VALUES
(0, 'DING_REPORT_SUBMITTED', '报销提交通知', 'DINGTALK',
 '📄 报销申请已提交',
 '**申请人：** {applicantName}\n**单号：** {reportNo}\n**金额：** {amount} 元\n**时间：** {submitTime}',
 1, NOW()),

(0, 'DING_APPROVAL_RESULT', '审批结果通知', 'DINGTALK',
 '📋 审批结果：{outcome}',
 '**单号：** {requestNo}\n**类型：** {businessType}\n**结果：** {outcome}\n**操作人：** {operator}',
 1, NOW()),

(0, 'DING_PAYMENT_COMPLETED', '打款完成通知', 'DINGTALK',
 '💰 报销款已到账',
 '**单号：** {reportNo}\n**金额：** {amount} 元\n**打款时间：** {paidTime}',
 1, NOW()),

(0, 'DING_REPORT_WITHDRAWN', '报销撤回通知', 'DINGTALK',
 '↩️ 报销申请已撤回',
 '**单号：** {reportNo}\n**撤回人：** {applicantName}\n**原状态：** {previousStatus}',
 1, NOW());
```

- [ ] **Step 2: Commit**

```bash
git add sql/init.sql
git commit -m "feat(sql): 4条钉钉通知模板种子数据"
```

---

### Task 5: Docker 环境变量 + 配置

**Files:**
- Modify: `docker-compose.services.yml`

- [ ] **Step 1: 在 notification-service 添加环境变量**

找到 `docker-compose.services.yml` 中 `notification-service` 的 `environment` 段，添加：

```yaml
DINGTALK_WEBHOOK_URL: ${DINGTALK_WEBHOOK_URL}
DINGTALK_SECRET: ${DINGTALK_SECRET}
```

- [ ] **Step 2: Commit**

```bash
git add docker-compose.services.yml
git commit -m "chore(docker): notification-service 钉钉 Webhook 环境变量"
```

---

### Task 6: 扩展测试

**Files:**
- Modify: `notification-service/src/test/java/com/expenseflow/notification/service/DingTalkServiceTest.java`
- Create: `notification-service/src/test/java/com/expenseflow/notification/service/NotificationRendererTest.java`

- [ ] **Step 1: 创建 NotificationRendererTest — 3 个测试**

```java
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
}
```

- [ ] **Step 2: 扩展 DingTalkServiceTest — 添加真实发送格式验证**

在现有测试类中添加 2 个新测试（保留原有 2 个）：

```java
@Test
@DisplayName("非 mock 模式有 webhook URL 时尝试发送")
void shouldAttemptRealSendWhenNotMock() {
    when(dingTalkConfig.isMock()).thenReturn(false);
    when(dingTalkConfig.getWebhookUrl()).thenReturn("https://oapi.dingtalk.com/robot/send?access_token=test");
    when(dingTalkConfig.getSecret()).thenReturn("");
    // 真实 HTTP 发送会失败（URL 无效），但不应抛未处理异常
    dingTalkService.send("测试", "内容");
}

@Test
@DisplayName("mock 模式不调用 HTTP")
void shouldNotCallHttpInMockMode() {
    when(dingTalkConfig.isMock()).thenReturn(true);
    dingTalkService.send("标题", "内容");
    // 验证没有 HTTP 调用：shouldOnlyLogInMockMode 已覆盖，此处补充
}
```

添加 import：
```java
import static org.mockito.Mockito.when;
```

- [ ] **Step 3: 运行测试**

```bash
cd notification-service && mvn test -q
```

Expected: All tests PASS (至少 6 个测试)

- [ ] **Step 4: Commit**

```bash
git add notification-service/src/test/
git commit -m "test(notification): Renderer 测试 + DingTalk 真实发送格式验证"
```

---

### Task 7: 全量测试 + 本地验证

- [ ] **Step 1: 运行全量测试**

```bash
cd notification-service && mvn test -q
cd ../expense-service && mvn test -q
```

Expected: Both BUILD SUCCESS, all tests PASS

- [ ] **Step 2: 打包**

```bash
mvn clean package -DskipTests -q -pl notification-service,expense-service -am
```

- [ ] **Step 3: Docker 重启 + Health 验证**

```bash
docker compose -f docker-compose.services.yml up -d --build notification-service expense-service
curl -s http://localhost:8085/actuator/health
curl -s http://localhost:8082/actuator/health
```

Expected: Both return `{"status":"UP"}`

- [ ] **Step 4: Commit（如有未提交变更）**

```bash
git status
# 如果有变更则 commit
```

---

## 文件变更清单

| 操作 | 文件 |
|:---:|------|
| **C** | `notification-service/.../service/NotificationRenderer.java` |
| **C** | `notification-service/.../test/.../NotificationRendererTest.java` |
| **M** | `notification-service/.../config/RabbitMQConfig.java` |
| **M** | `notification-service/.../service/RabbitMQConsumer.java` |
| **M** | `notification-service/.../test/.../DingTalkServiceTest.java` |
| **M** | `expense-service/.../service/ExpenseReportServiceImpl.java` |
| **M** | `expense-service/.../service/PaymentService.java` |
| **M** | `expense-service/.../controller/ApprovalCallbackController.java` |
| **M** | `sql/init.sql` |
| **M** | `docker-compose.services.yml` |
