# ExpenseFlow 概要设计文档

> 版本：v1.0 | 最后更新：2026-05-14

---

## 目录

1. [系统功能结构](#1-系统功能结构)
2. [微服务模块设计](#2-微服务模块设计)
3. [接口设计概要](#3-接口设计概要)
4. [工作流设计](#4-工作流设计)
5. [规则引擎设计](#5-规则引擎设计)
6. [AI 模块设计](#6-ai-模块设计)
7. [通知模块设计](#7-通知模块设计)

---

## 1. 系统功能结构

```
ExpenseFlow 差旅报销智能管理平台
│
├── gateway-service (8080) — 统一网关
│   ├── JWT 验签 & 令牌透传
│   ├── Sentinel 限流（100 req/s/用户）
│   ├── CORS 跨域处理
│   └── 路由转发至下游微服务
│
├── system-service (8081) — 系统管理
│   ├── 租户管理 (SysTenant)
│   ├── 用户认证 (AuthController)
│   │   ├── 登录/登出 (JWT AccessToken 2h + RefreshToken 7d)
│   │   ├── Token 刷新 & 黑名单 (Redis)
│   │   └── 用户信息查询 (/me)
│   ├── 用户管理 (UserController) — CRUD
│   ├── 角色管理 (RoleController) — CRUD
│   ├── 权限管理 (PermissionController) — CRUD
│   ├── RBAC 授权 (SecurityConfig + @PreAuthorize)
│   ├── 部门管理 (DepartmentController)
│   ├── 员工管理 (EmployeeController)
│   ├── 字典管理 (DictController)
│   ├── OAuth/SSO (OAuthController)
│   └── 多租户拦截 (ExpenseFlowTenantLineHandler)
│
├── expense-service (8082) — 差旅报销
│   ├── 出差申请 (TravelRequestController)
│   │   ├── 草稿保存 / 修改 / 删除
│   │   ├── 提交 → Feign 调用批准服务
│   │   └── 撤回
│   ├── 报销单 (ExpenseReportController)
│   │   ├── 草稿保存 / 修改 / 删除
│   │   ├── 明细管理 (ExpenseItemController)
│   │   ├── 提交 → 政策校验 → Feign 启动审批 → RabbitMQ AI 审单
│   │   └── 撤回
│   ├── 发票管理 (InvoiceController)
│   ├── 消费记录 (CostRecordController)
│   ├── 费用政策 (ExpensePolicyController)
│   ├── 打款流水 (PaymentRecordController)
│   └── 审批回调接收 (ApprovalCallbackController)
│       └── 接收审批结果 → 更新状态 → 发送 RabbitMQ 通知事件
│
├── approval-service (8083) — 审批引擎
│   ├── 流程启动 (ApprovalProcessController)
│   │   └── 接收 Feign 请求 → Drools 规则匹配 → Flowable 启动流程
│   ├── 任务管理 (ApprovalTaskController)
│   │   ├── 待办列表 (按候选组 / 负责人筛选)
│   │   ├── 签收 (claim) + 完成 (complete)
│   │   ├── 委派 (delegate)
│   │   └── 审批记录查询
│   ├── Drools 规则引擎 (DroolsRuleService)
│   │   ├── 金额阈值自动判断 needDirector
│   │   └── Java 容错降级（Drools 不可用时）
│   └── 流程回调 (ApprovalCallbackDelegate)
│       └── ExecutionListener → Feign 回写 expense-service
│
├── ai-service (8084) — AI 智能
│   ├── OCR 识别 (OcrController)
│   │   ├── 发票图片上传解析
│   │   └── 异步处理 + 结果查询
│   ├── DeepSeek 审单 (ReviewController)
│   │   ├── 智能审批评估 (evaluate)
│   │   ├── 风险分析 (analyzeRisk)
│   │   └── RabbitMQ 消费报销单提交事件
│   └── RAG 问答 (RagController)
│       ├── 差旅政策智能问答
│       └── 内置政策语料库
│
├── notification-service (8085) — 通知推送
│   ├── 站内消息 (MessageController)
│   │   ├── 消息分页 / 已读 / 全部已读 / 未读计数
│   │   └── RabbitMQ 消费审批结果事件
│   ├── 钉钉机器人 (DingTalkService)
│   │   ├── 真实 Webhook + HMAC-SHA256 加签
│   │   ├── Markdown 格式推送
│   │   └── Mock 模式（开发/演示用）
│   └── 消息模板 (TemplateController)
│       └── CRUD + 占位符渲染
│
└── expense-common (公共模块)
    ├── BaseEntity (id/tenantId/createTime/updateTime/deleted)
    ├── 多租户拦截器 (TenantContextFilter)
    ├── MyBatis-Plus 租户行处理器 (ExpenseFlowTenantLineHandler)
    ├── 审计日志切面 (AuditLogAspect)
    └── 统一响应体 (Result<T>)
```

---

## 2. 微服务模块设计

### 2.1 gateway-service (8080)

**职责**：统一入口，JWT 验签、Sentinel 限流、路由转发。

**核心类**：
| 类 | 说明 |
|---|---|
| `GatewayConfig` | Sentinel 限流回调配置，超限返回 429 JSON |

**关键行为**：
- 请求到达 Gateway → 提取 JWT → 验签 → 提取 userId/tenantId → 写入请求头 → 转发下游
- Sentinel 每用户 100 req/s，触发限流返回 `{"code":429,"message":"请求过于频繁，请稍后再试"}`

### 2.2 system-service (8081)

**职责**：租户、用户、角色、权限的 RBAC 管理，JWT 认证与刷新，多租户数据隔离。

**核心类**：
| 类别 | 类 | 说明 |
|---|---|---|
| Controller | `AuthController` | `/system/auth/**` 放行，login/logout/refresh/me |
| Controller | `UserController` | 用户 CRUD |
| Controller | `RoleController` | 角色 CRUD |
| Controller | `PermissionController` | 权限 CRUD |
| Controller | `TenantController` | 租户管理 |
| Controller | `DepartmentController` | 部门管理 |
| Controller | `EmployeeController` | 员工管理 |
| Controller | `OAuthController` | OAuth2/SSO 集成 |
| Service | `AuthServiceImpl` | JWT 签发(passToken 2h + refreshToken 7d)、BCrypt 验密、角色权限缓存至 Redis |
| Config | `SecurityConfig` | Spring Security 无状态模式，公开 /system/auth/**，其余需认证 |
| Config | `JwtAuthFilter` | OncePerRequestFilter，从 Authorization header 提取 token，验证后注入 SecurityContext |
| Config | `MybatisPlusConfig` | 注册 ExpenseFlowTenantLineHandler 多租户拦截 |
| Handler | `ExpenseFlowTenantLineHandler` | ThreadLocal 存储 tenantId，MyBatis-Plus 自动注入 SQL 条件 |

**认证流程**：
```
POST /system/auth/login {username, password}
  → BCrypt 验证
  → 生成 UUID tokenId
  → passToken = JwtUtil.generateAccessToken(userId, tenantId, tokenId)  // 2h
  → refreshToken = JwtUtil.generateRefreshToken(userId, tenantId, tokenId)  // 7d
  → Redis 缓存: user:{userId}, token:{tokenId}, user:perm:{userId}
  → 返回 TokenVO { passToken, refreshToken, expiresIn: 7200, userVO }
```

**登出**：Token 加入 Redis 黑名单 `token:blacklist:{tokenId}`，有效期对齐 Token 剩余时间。

### 2.3 expense-service (8082)

**职责**：出差申请与报销单全生命周期管理，Feign 调用审批服务，RabbitMQ 发布事件。

**核心类**：
| 类别 | 类 | 说明 |
|---|---|---|
| Controller | `TravelRequestController` | 出差申请 CRUD + submit/withdraw |
| Controller | `ExpenseReportController` | 报销单 CRUD + submit/withdraw |
| Controller | `ApprovalCallbackController` | 接收审批结果回调 `/expense/callback/approval-result` |
| Controller | `InvoiceController` | 发票管理 |
| Controller | `CostRecordController` | 消费记录 |
| Controller | `ExpensePolicyController` | 费用政策 CRUD |
| Controller | `PaymentRecordController` | 打款流水 |
| Service | `TravelRequestServiceImpl` | 创建时自动生成 requestNo (TRV-xxx)，提交时调 Feign 启动审批 |
| Service | `ExpenseReportServiceImpl` | 创建/提交时校验明细、校验政策(PolicyValidator)、Feign 启动审批、RabbitMQ 发送 AI 审单消息 |
| Feign | `ApprovalFeignClient` | 调用 approval-service `/approval/process/start` |
| Feign | `SystemFeignClient` | 查用户/部门信息 |
| Fallback | `ApprovalFeignFallbackFactory` | approval-service 不可用时返回 fallback processInstanceId |
| Util | `PolicyValidator` | 遍历明细 vs 费用政策表，超出上限生成警告 |
| Config | `RabbitMQConfig` | Jackson2JsonMessageConverter |

**出差申请提交流程**：
```
POST /expense/travel/{id}/submit
  → 校验状态必须为 DRAFT
  → Feign 获取用户名 (SystemFeignClient.getUser)
  → 构造 ApprovalStartDTO {businessType: "TRAVEL_REQUEST", businessId, requestNo, applicantId, applicantName, amount}
  → Feign → approval-service /approval/process/start
  → 更新状态 → "APPROVING"，写入 processInstanceId
  → 如 Feign 失败 → FallbackFactory 生成 fallback-pi-xxx
```

**报销单提交流程**：
```
POST /expense/report/{id}/submit
  → 校验状态必须为 DRAFT
  → 校验至少 1 条明细
  → 汇总金额 + PolicyValidator.validate(items) 政策校验
  → Feign → approval-service 启动审批
  → RabbitMQ 发送 ai.review.queue 消息 {eventId, reportId, amount, tenantId}
  → 更新状态 → "APPROVING"
```

### 2.4 approval-service (8083)

**职责**：Flowable 工作流引擎 + Drools 规则引擎，审批流转与回调。

**核心类**：
| 类别 | 类 | 说明 |
|---|---|---|
| Controller | `ApprovalProcessController` | 启动审批流程 `/approval/process/start` |
| Controller | `ApprovalTaskController` | 待办查询、任务完成、委派、审批记录 |
| Service | `ApprovalProcessServiceImpl` | 接收 DTO → Drools 评估 needDirector → 选 BPMN 流程 → startProcessInstanceByKey |
| Service | `ApprovalTaskServiceImpl` | 任务查询(按候选组/负责人)、签收(claim)、完成(complete + outcome变量)、委派(delegate) |
| Service | `DroolsRuleService` | 执行 DRL 规则文件，失败时 Java 容错 |
| Config | `FlowableConfig` | 设置中文字体 SimSun |
| Config | `DroolsConfig` | KieContainer 加载 `classpath:rules/*.drl`，编译失败返回 null 降级 |
| Delegate | `ApprovalCallbackDelegate` | Flowable ExecutionListener，流程结束时 Feign 回调 expense-service |
| Feign | `ExpenseFeignClient` | 回调 expense-service `/expense/callback/approval-result` |
| Feign | `SystemFeignClient` | 查询用户信息 |

**流程启动逻辑**：
```
ApprovalStartDTO
  → DroolsRuleService.evaluate(businessType, amount)
    → businessType 路由流程定义:
      TRAVEL_REQUEST  → travel-request-approval
      EXPENSE_REPORT  → expense-report-approval
  → RuntimeService.startProcessInstanceByKey(processDefKey, variables)
  → 返回 ProcessStartResponse { processInstanceId, approvalLevel, warnings }
```

**任务完成逻辑**：
```
POST /approval/task/{taskId}/complete {action, comment}
  → 签收任务 (claim) 如尚未签收
  → 写入 ApApprovalRecord (businessType/businessId/processInstanceId/taskId/approverId/action/comment)
  → action 映射: APPROVE → outcome=APPROVED, REJECT → outcome=REJECTED
  → taskService.complete(taskId, {outcome})
```

### 2.5 ai-service (8084)

**职责**：OCR 发票识别、DeepSeek 智能审单、RAG 政策问答。

**核心类**：
| 类别 | 类 | 说明 |
|---|---|---|
| Controller | `OcrController` | POST /ai/ocr/recognize, GET /ai/ocr/{id} |
| Controller | `ReviewController` | POST /ai/review/evaluate, POST /ai/review/risk |
| Controller | `RagController` | POST /ai/rag/ask |
| Service | `OcrServiceImpl` | @Async 异步 OCR，Mock 实现，返回标准发票字段 |
| Service | `DeepSeekReviewServiceImpl` | 构建审单 prompt → ChatLanguageModel.generate() → 解析结果/风险等级 → 落库；失败时 Mock 降级 |
| Service | `RagServiceImpl` | 内置 POLICY_CONTEXT + 用户问题 → ChatLanguageModel.generate() |
| Config | `LangChain4jConfig` | 配置 OpenAiChatModel(baseUrl=api.deepseek.com, model=deepseek-chat, timeout=30s, maxRetries=2) |
| Config | `RabbitMQConfig` | 声明 exchange/queue/binding: expense.exchange → ai.review.queue (routing key: expense.report.submitted) |
| Consumer | `RabbitMQConsumer` | @RabbitListener(queues="ai.review.queue") 消费报销单提交事件 → DeepSeekReviewService.review() |

### 2.6 notification-service (8085)

**职责**：站内消息 + 钉钉机器人双通道通知。

**核心类**：
| 类别 | 类 | 说明 |
|---|---|---|
| Controller | `MessageController` | 消息分页、标记已读、全部已读、未读计数 |
| Controller | `TemplateController` | 消息模板 CRUD |
| Service | `MessageServiceImpl` | 站内消息写入 NtMessage 表 |
| Service | `DingTalkService` | HMAC-SHA256 加签 → Webhook Markdown 推送；Mock 模式仅打印日志 |
| Service | `TemplateServiceImpl` | 模板管理，支持占位符 |
| Config | `DingTalkConfig` | @ConfigurationProperties(prefix="dingtalk") mock/webhookUrl/secret |
| Config | `RabbitMQConfig` | 绑定两条路由: expense.result.notified + ai.review.completed → notification.event.queue |
| Consumer | `RabbitMQConsumer` | @RabbitListener 消费通知事件 → send 站内消息 + dingTalk.send() |

---

## 3. 接口设计概要

### 3.1 服务间调用关系图

```
                    ┌──────────────┐
                    │   Gateway    │  :8080
                    └──────┬───────┘
           ┌───────────────┼───────────────┐
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │   System    │ │   Expense   │ │  Approval   │
    │   :8081     │ │   :8082     │ │   :8083     │
    └──────┬──────┘ └──┬───┬───┬──┘ └──┬───┬──────┘
           │            │   │   │       │   │
           │   Feign    │   │   │ Feign │   │
           │◄───────────┘   │   │──────►│   │
           │                │   │        │   │
           │                │   │ Feign  │   │
           │                │   │◄───────┘   │
           │          ┌─────┘   │            │
           │          │         │            │
           │  RabbitMQ│         │  RabbitMQ  │
           │          │         │            │
    ┌──────▼──────┐   │   ┌─────▼──────┐     │
    │Notification │   │   │     AI     │     │
    │   :8085     │   │   │   :8084    │     │
    └─────────────┘   │   └────────────┘     │
                      │                      │
                      └──────────────────────┘
                      Feign 同步调用
                      ─ ─ RabbitMQ 异步消息
```

### 3.2 OpenFeign 同步调用清单

| 调用方 | 被调用方 | 接口 | 用途 | Fallback |
|---|---|---|---|---|
| expense-service | approval-service | POST /approval/process/start | 启动审批流程 | 生成 fallback-pi-xxx |
| expense-service | system-service | GET /system/user/{id} | 获取用户名 | 返回 "未知" |
| expense-service | system-service | GET /system/dept/{id} | 获取部门名 | 返回 "未知" |
| approval-service | expense-service | PUT /expense/callback/approval-result | 流程结束回调更新状态 | 记录日志 |
| approval-service | system-service | GET /system/user/{id} | 获取用户名 | 返回 "未知" |
| ai-service | system-service | GET /system/user/{id} | 获取用户信息 | — |
| ai-service | expense-service | — | (预留) | — |
| notification-service | system-service | — | (预留) | — |

### 3.3 RabbitMQ 异步消息

```
Exchange: expense.exchange (Topic)

Queue                          Routing Key                     消费者
─────────────────────────────────────────────────────────────────────
ai.review.queue                expense.report.submitted         ai-service
notification.event.queue       expense.result.notified          notification-service
notification.event.queue       ai.review.completed              notification-service
```

**消息体格式**（统一 JSON）：
```json
{
  "eventId": "uuid（用于去重）",
  "eventType": "approval.result",
  "businessType": "TRAVEL_REQUEST | EXPENSE_REPORT",
  "businessId": 123,
  "outcome": "APPROVED | REJECTED",
  "tenantId": 0,
  "applicantId": 1
}
```

**消息发送端**：
- expense-service `ApprovalCallbackController` 收到审批回调后 → 发送 `expense.result.notified`
- expense-service `ExpenseReportServiceImpl.submit()` 提交报销单后 → 发送 `expense.report.submitted`

---

## 4. 工作流设计

### 4.1 BPMN 2.0 流程总览

Flowable 引擎部署两个流程定义：

| 流程 ID | 流程名称 | 入口条件 | 流程参数 |
|---|---|---|---|
| `travel-request-approval` | 出差申请审批 | businessType=TRAVEL_REQUEST | amount, needDirector, applicantId, ... |
| `expense-report-approval` | 报销单审批 | businessType=EXPENSE_REPORT | amount, applicantId, ... |

### 4.2 出差申请审批流程 (travel-request-approval)

```
  ┌───────┐
  │ Start │
  └───┬───┘
      │
 ┌────▼────────────┐
 │  经理审批        │ candidateGroups="manager"
 │ (userTask)      │
 └────┬────────────┘
      │
 ┌────▼────┐   REJECTED    ┌──────────┐
 │ GW1     ├──────────────►│ 审批驳回  │
 │ (excl.) │               │ (end, callback)
 └────┬────┘               └──────────┘
      │ default
 ┌────▼────────────┐  needDirector=true   ┌──────────────┐
 │ GW DirectorCheck├────────────────────► │ 总监审批      │
 │ (excl.)         │                      │ candidateGroups="director"
 └────┬────────────┘                      └──────┬───────┘
      │ default (skip)                           │
      │                                          │
      │                               ┌──────────▼──────┐
      │                               │ GW2     │ default│
      │                               │ (excl.) ├────────┤
      │                               └────┬─────┘        │
      │                        REJECTED     │              │
      │                    ┌────────────────┘              │
      │                    ▼                               ▼
      │              ┌──────────┐                ┌──────────────┐
      └──────────────► 审批通过  │                │   审批驳回    │
                     │ (end, callback)            │ (end, callback)
                     └──────────┘                └──────────────┘
```

**关键设计**：
- **经理审批** (managerApproval)：首个用户任务，候选组 `manager`
- **GW1** 排他网关：`outcome == 'REJECTED'` → 直接驳回；默认 → 进入总监判定
- **GW DirectorCheck** 排他网关：`needDirector == true` → 总监审批；默认跳过 → 审批通过
- **总监审批** (directorApproval)：候选组 `director`，仅在 Drools 判定金额 > 5000 时触发
- **GW2** 排他网关：`outcome == 'REJECTED'` → 驳回；默认 → 通过
- 两个 EndEvent 均绑定 `ExecutionListener(delegateExpression="${approvalCallbackDelegate}")`

### 4.3 报销单审批流程 (expense-report-approval)

```
  ┌───────┐
  │ Start │
  └───┬───┘
      │
 ┌────▼────────────┐
 │  财务审核        │ candidateGroups="finance"
 │ (userTask)      │
 └────┬────────────┘
      │
 ┌────▼───────┐   REJECTED    ┌──────────┐
 │ GW Finance │──────────────►│ 审批驳回  │
 │ (excl.)    │               │ (end, callback)
 └────┬───────┘               └──────────┘
      │ default
 ┌────▼────────────┐
 │  经理审批        │ candidateGroups="manager"
 │ (userTask)      │
 └────┬────────────┘
      │
 ┌────▼───────┐   REJECTED    ┌──────────┐
 │ GW Manager │──────────────►│ 审批驳回  │
 │ (excl.)    │               │ (end, callback)
 └────┬───────┘
      │ default
 ┌────▼──────────────┐
 │  审批通过          │ (end, callback)
 └───────────────────┘
```

**关键设计**：
- **财务审核** (financeVerify)：首节点，候选组 `finance`，负责发票/金额合规性
- **GW Finance**：驳回 → end；默认通过 → 经理审批
- **经理审批** (managerApproval)：候选组 `manager`，二次确认
- **GW Manager**：驳回 → end；默认通过 → end
- 两个 EndEvent 均绑定 ExecutionListener

### 4.4 回调机制 (ExecutionListener)

```
流程结束 (任一 EndEvent 的 end 事件)
  → ApprovalCallbackDelegate.notify(DelegateExecution)
  → 提取变量: businessType, businessId, outcome (null时默认 APPROVED)
  → 构造 ApprovalCallbackDTO
  → ExpenseFeignClient.updateApprovalResult(callback)
  → PUT /expense/callback/approval-result
    → 更新 ExTravelRequest.status / ExExpenseReport.status
    → RabbitMQ 发送 expense.result.notified 事件
```

---

## 5. 规则引擎设计

### 5.1 Drools 规则 (approval-rules.drl)

3 条规则，定义在 `classpath:rules/approval-rules.drl`：

```java
// 规则1：出差申请金额 > 5000 → 需要总监审批
rule "High amount travel request needs director"
  when
    $input: RuleInput(businessType == "TRAVEL_REQUEST", amount > 5000)
    $output: RuleOutput()
  then
    $output.setNeedDirector(true);
end

// 规则2：报销单金额 > 10000 → 风险警告
rule "High amount expense report warning"
  when
    $input: RuleInput(businessType == "EXPENSE_REPORT", amount > 10000)
    $output: RuleOutput()
  then
    $output.getWarnings().add("报销金额较大，需重点关注");
end

// 规则3：报销单金额 > 20000 → 建议总监复核
rule "Very high amount expense report needs extra review"
  when
    $input: RuleInput(businessType == "EXPENSE_REPORT", amount > 20000)
    $output: RuleOutput()
  then
    $output.getWarnings().add("报销金额超过20000，建议总监复核");
end
```

**输入输出模型**：
- `RuleInput(businessType, amount)` — 业务类型 + 金额
- `RuleOutput(needDirector=false, warnings=[])` — 是否需要总监 + 警告信息列表

### 5.2 Java 容错机制

`DroolsRuleService` 构造时使用 `@Autowired(required=false) KieContainer`：
- 正常：`kieContainer.newKieSession()` → 插入 input/output → `fireAllRules()` → 返回结果
- 异常/未加载：kieContainer 为 null → 执行 Java fallback 代码（逻辑与 DRL 完全等价）

```java
// Java fallback 等价逻辑
if ("TRAVEL_REQUEST".equals(businessType) && amount > 5000)  output.setNeedDirector(true);
if ("EXPENSE_REPORT".equals(businessType) && amount > 10000) output.getWarnings().add("...");
if ("EXPENSE_REPORT".equals(businessType) && amount > 20000) output.getWarnings().add("...");
```

DroolsConfig 加载 DRL 时编译失败返回 null，确保 Drools 9.x 兼容性问题不影响核心流程。

### 5.3 规则在流程中的使用

`ApprovalProcessServiceImpl.startProcess()` 调用 `droolsRuleService.evaluate()`→ 结果写入 Flowable 流程变量 `needDirector` → BPMN 中 GW DirectorCheck 根据此变量决定是否走向总监审批节点。

---

## 6. AI 模块设计

### 6.1 OCR 识别流程

```
POST /ai/ocr/recognize {invoiceId, imageUrl}
  → 创建 AiOcrResult (status=PROCESSING) → 入库
  → @Async doOcr() 异步执行:
    → [Mock] sleep 800ms → 模拟识别结果:
      - parsedInvoiceNo = "MOCK-INV-{id}"
      - parsedAmount = 100.00
      - parsedSellerName = "模拟销售方"
      - confidence = 0.95
    → 更新 status=SUCCESS, 写入解析字段
  → 同步返回 AiOcrResult (status=PROCESSING)
  → 客户端通过 GET /ai/ocr/{id} 轮询结果
```

**识别的字段**：发票号码、金额、开票日期、销售方名称、销售方税号、置信度。

**降级策略**：当前为 Mock 实现，结构预留阿里云 OCR SDK 接入（`OcrConfig` 中 AppCode 配置已就绪）。

### 6.2 DeepSeek 审单流程

**同步调用**（`POST /ai/review/evaluate`）：
```
ReviewRequestDTO {businessType, businessId, totalAmount, items[]}
  → buildReviewPrompt(): 构建结构化 prompt
    "你是一个企业差旅报销审核专家。请审核以下报销申请：
     业务类型：EXPENSE_REPORT
     报销总金额：8500.00元
     明细：
       - 住宿费: 420.00元
       - 交通费: 8080.00元
     请输出审核结果（格式：结果:{APPROVED|REVIEW_NEEDED|REJECTED} 风险等级:{LOW|MEDIUM|HIGH} 意见:<审核意见>）"
  → chatModel.generate(prompt) — 调用 DeepSeek Chat API
  → 解析响应: extractResult() / extractRiskLevel()
  → 存储 AiReviewResult (model="deepseek-chat", confidence=0.88)
```

**异步消费**（`RabbitMQConsumer.onReportSubmitted()`）：
```
消费 ai.review.queue 消息 {reportId, amount, tenantId}
  → 构造 ReviewRequestDTO → reviewService.review()
  → DeepSeek 审单 → 结果持久化
```

**Mock 降级**（DeepSeek API 不可用时）：
```
金额 > 10000 → 结果:REVIEW_NEEDED, 风险:MEDIUM
金额 > 5000  → 结果:APPROVED,  风险:LOW
其他        → 结果:APPROVED,  风险:LOW（小额自动通过）
```

### 6.3 RAG 问答流程

```
POST /ai/rag/ask {question}
  → 拼装 System Prompt: 差旅费用政策参考（7 条规则，硬编码常量 POLICY_CONTEXT）
    + "用户问题: " + question
    + "请基于上述政策回答，简洁明了。"
  → chatModel.generate(prompt)
  → 返回 RagAnswerVO {question, answer, model="deepseek-chat"}
  → API 失败时降级: "抱歉，AI 服务暂时不可用，请稍后重试。"
```

**内置政策语料**（7 条）：
1. 交通费标准（高铁二等座、飞机经济舱，超标需总监审批）
2. 住宿费上限（一线城市 500/天，其他 350/天）
3. 餐费补助（100/天，无需发票）
4. 出差申请金额 > 5000 需总监审批
5. 报销单需关联已审批的出差申请单
6. 增值税专用发票可抵扣
7. 审批流程说明

### 6.4 LangChain4j 配置

`LangChain4jConfig` 通过 `OpenAiChatModel` 适配 DeepSeek API（兼容 OpenAI 协议）：
- baseUrl: `https://api.deepseek.com`
- apiKey: `${langchain4j.open-ai.chat-model.api-key}`
- modelName: `deepseek-chat`
- timeout: 30s, maxRetries: 2
- 启用请求/响应日志

---

## 7. 通知模块设计

### 7.1 双通道架构

```
                    RabbitMQ Consumer
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
     ┌──────────────┐          ┌────────────────┐
     │  站内消息     │          │  钉钉机器人     │
     │ (NtMessage)  │          │ (DingTalkService)
     └──────────────┘          └────────────────┘
     存储到 MySQL              Webhook + HMAC-SHA256
     消息中心展示              Markdown 格式推送
```

**触发时机**：
- 审批结果回调 → expense-service 发送 `expense.result.notified` → notification-service 消费
- AI 审单完成（预留 `ai.review.completed` routing key）

### 7.2 站内消息 (NtMessage)

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 自增主键 |
| tenantId | Long | 多租户隔离 |
| userId | Long | 接收人 |
| messageType | String | NOTIFICATION / WARNING / ERROR |
| title | String | 消息标题 |
| content | String | 消息内容 |
| businessType | String | 关联业务类型 |
| businessId | Long | 关联业务 ID |
| isRead | Integer | 0=未读, 1=已读 |
| readTime | LocalDateTime | 阅读时间 |
| createTime | LocalDateTime | 创建时间 |

**API**：
- `GET /notification/message/page` — 分页查询（按用户、倒序）
- `PUT /notification/message/{id}/read` — 标记已读
- `PUT /notification/message/read-all` — 全部已读
- `GET /notification/message/unread-count` — 未读计数

### 7.3 钉钉机器人 (DingTalkService)

**配置** (`application.yml` 或 Nacos)：
```yaml
dingtalk:
  mock: true          # true=仅打印日志, false=真实推送
  webhook-url: ""     # 钉钉机器人 Webhook 地址
  secret: ""          # 加签密钥
```

**加签算法**（符合钉钉自定义机器人安全规范）：
```
timestamp = System.currentTimeMillis()
stringToSign = timestamp + "\n" + secret
sign = URLEncode(Base64(HmacSHA256(stringToSign, secret)))
webhookUrl += "?timestamp=" + timestamp + "&sign=" + sign
```

**消息格式**（Markdown）：
```json
{
  "msgtype": "markdown",
  "markdown": {
    "title": "审批结果通知",
    "text": "## 审批结果通知\n\n您的EXPENSE_REPORT已通过"
  }
}
```

**Mock 模式**：当 `dingtalk.mock=true` 时，仅 `log.info("[钉钉 Mock] 发送消息: title={}, content={}")`，不发起 HTTP 请求。

### 7.4 消息模板 (NtNotificationTemplate)

| 字段 | 类型 | 说明 |
|---|---|---|
| templateCode | String | 模板编码 (e.g. approval_result) |
| templateName | String | 模板名称 |
| channel | String | 推送渠道 (IN_APP / DINGTALK) |
| titleTemplate | String | 标题模板（含占位符） |
| contentTemplate | String | 内容模板（含占位符） |
| status | Integer | 1=启用, 0=禁用 |

**占位符示例**：
```
标题: "审批结果通知 — {businessType}"
内容: "您的{businessTypeName}已于{approveTime}{result}，审批意见: {comment}"
```

**API**：
- `GET /notification/template/list` — 查询所有启用模板
- `GET /notification/template/{code}` — 按编码查询
- `POST /notification/template` — 创建模板
- `PUT /notification/template/{id}` — 修改模板

---

## 附录：技术决策摘要

| 决策点 | 选择 | 原因 |
|---|---|---|
| 工作流引擎 | Flowable 7 | 轻量级，BPMN 2.0 标准，Spring Boot 原生集成 |
| 规则引擎 | Drools 9 + Java Fallback | 业务规则可视化，Java 兜底确保可用性 |
| AI 框架 | LangChain4j 0.35 | 统一 LLM 调用抽象，无需锁定供应商 |
| AI 模型 | DeepSeek Chat (deepseek-chat) | OpenAI 兼容协议，成本可控 |
| OCR | 阿里云 OCR SDK (AppCode) | 国内发票识别准确率高 |
| 消息队列 | RabbitMQ 3.13 | 可靠投递，Topic Exchange 灵活路由 |
| 缓存 | Redis 7 | JWT 黑名单 + 用户权限缓存 |
| 多租户 | 共享数据库 + tenant_id 列 | 开发阶段简化部署，MyBatis-Plus TenantLineHandler 自动注入 |
| 序列化 | Jackson2JsonMessageConverter | RabbitMQ JSON 消息统一格式 |
