# P1-3 钉钉真实推送 — 设计文档

> 版本：v1.0 | 日期：2026-05-16 | 状态：已确认，待实施

## 背景

DingTalkService 真实推送的代码逻辑已完整实现（HMAC-SHA256 加签 + Markdown 消息 + HTTP POST），但 `application.yml` 中 `mock: false` 无实际 Webhook URL，消息从未真实发出。RabbitMQConsumer 只硬编码处理审批结果事件，且 `expense.report.submitted` 缺少 Queue 绑定。

目标：打通 4 类关键事件的钉钉通知链路，利用已有的 `NtNotificationTemplate` 模板系统做消息渲染。

## 事件 → 模板映射

| 事件 | routing key | 发布位置 | 模板 code |
|---|---|---|---|
| 报销提交 | `expense.report.submitted` | ExpenseReportServiceImpl.submit()（已有）| `DING_REPORT_SUBMITTED` |
| 审批结果 | `expense.result.notified` | ApprovalCallbackController（已有）| `DING_APPROVAL_RESULT` |
| 打款完成 | `expense.payment.completed` | ExpenseReportServiceImpl（新增）| `DING_PAYMENT_COMPLETED` |
| 报销撤回 | `expense.report.withdrawn` | ExpenseReportServiceImpl.withdraw()（新增）| `DING_REPORT_WITHDRAWN` |

模板占位符：`{var}` 语法，由 Consumer 从事件 Map 取值替换。

## RabbitMQ 绑定

| routing key | 状态 | 操作 |
|---|---|---|
| `expense.result.notified` | 已绑定 | 保持 |
| `ai.review.completed` | 已绑定 | 保持 |
| `expense.report.submitted` | **已发布但无绑定（bug）** | 新增绑定 |
| `expense.report.withdrawn` | 不存在 | 新增发布 + 绑定 |
| `expense.payment.completed` | 不存在 | 新增发布 + 绑定 |

## Consumer 改造

```java
@RabbitListener(queues = "notification.event.queue")
public void onNotificationEvent(Map<String, Object> map) {
    // 1. 幂等检查（现有逻辑保留）
    // 2. 提取 eventType → 映射 templateCode
    // 3. templateService.getByCode(templateCode) → 渲染 → dingTalkService.send()
    // 4. 站内消息同步发送（现有逻辑保留）
    // 5. 模板不存在 → 降级用默认文案
}
```

事件 → templateCode 映射：
- `EXPENSE_SUBMITTED` → `DING_REPORT_SUBMITTED`
- `APPROVAL_RESULT` → `DING_APPROVAL_RESULT`
- `PAYMENT_COMPLETED` → `DING_PAYMENT_COMPLETED`
- `REPORT_WITHDRAWN` → `DING_REPORT_WITHDRAWN`

`render(String template, Map<String,Object> data)` 方法：匹配 `{varName}` 替换为 `data.get("varName")` 的值。

## 新增事件发布点

### ExpenseReportServiceImpl.withdraw()

在状态更新后发布：
```java
Map<String, Object> event = Map.of(
    "eventId", UUID.randomUUID().toString(),
    "eventType", "REPORT_WITHDRAWN",
    "reportNo", r.getReportNo(),
    "applicantName", user != null ? user.getRealName() : "未知",
    "previousStatus", previousStatus,
    "tenantId", 0
);
rabbitTemplate.convertAndSend("expense.exchange", "expense.report.withdrawn", event);
```

### PaymentRecord 创建处

打款记录保存后发布：
```java
Map<String, Object> event = Map.of(
    "eventId", UUID.randomUUID().toString(),
    "eventType", "PAYMENT_COMPLETED",
    "reportNo", reportNo,
    "amount", payment.getAmount(),
    "paidTime", payment.getPaidTime().toString(),
    "tenantId", 0
);
rabbitTemplate.convertAndSend("expense.exchange", "expense.payment.completed", event);
```

## 模板种子数据

```sql
INSERT INTO nt_notification_template
(tenant_id, template_code, template_name, channel, title_template, content_template, status, create_time)
VALUES
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

## Docker 配置

`docker-compose.services.yml` 的 notification-service 环境变量：
```yaml
DINGTALK_WEBHOOK_URL: ${DINGTALK_WEBHOOK_URL}
DINGTALK_SECRET: ${DINGTALK_SECRET}
```

## 测试策略

| # | 测试 | 方式 |
|---|------|------|
| 1 | `DingTalkService.send()` 非 mock 模式 | Mock Webhook URL 返回 200，验证 HTTP 请求体格式 |
| 2 | Consumer 事件→模板映射 | 注入 4 种事件 Map，验证调用 `templateService.getByCode()` 参数 |
| 3 | 模板渲染逻辑 | `render("{name}了{action}", Map.of("name","张三","action","提交"))` → "张三了提交" |
| 4 | 模板不存在降级 | `getByCode` 返回 null 时不抛异常，用默认文案 |

## 验证标准

1. `mvn test` notification-service 全量通过
2. Docker 启动全服务，创建报销单 → 提交 → 审批 → 打款，钉钉群收到 4 条消息
3. Webhook URL 从 `docs/环境变量.txt` 获取
