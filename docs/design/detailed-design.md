# ExpenseFlow 详细设计文档

> 版本：v1.0 | 最后更新：2026-05-14

---

## 目录

1. [核心业务流程详解](#1-核心业务流程详解)
2. [状态机设计](#2-状态机设计)
3. [异常处理设计](#3-异常处理设计)
4. [配置管理](#4-配置管理)

---

## 1. 核心业务流程详解

### 1.a 登录认证流程

**时序描述**（参与者：Frontend → Gateway → System-Service → MySQL → Redis）

1. 用户在登录页输入 username/password，前端发送 `POST /system/auth/login`
2. 请求经 Gateway，`/system/auth/**` 在 SecurityConfig 中配置为 `permitAll()`，无需 JWT
3. `AuthController.login()` 接收 `LoginDTO {username, password}`
4. `AuthServiceImpl.login()`:
   - 根据 username 查询 `sys_user` 表（MyBatis-Plus LambdaQueryWrapper）
   - 使用 `BCryptPasswordEncoder.matches()` 验证密码
   - 检查用户状态 `status != 0`（0=禁用）
   - 生成 UUID tokenId（去除连字符，32位）
   - 调用 `JwtUtil.generateAccessToken(userId, tenantId, tokenId)` → 有效期 2h
   - 调用 `JwtUtil.generateRefreshToken(userId, tenantId, tokenId)` → 有效期 7d
   - 缓存至 Redis：
     - `user:{userId}` → UserVO（30min TTL）
     - `token:{tokenId}` → "1"（2h TTL）
     - `user:perm:{userId}` → Set<roleCode>（30min TTL，从 sys_user_role + sys_role 查得）
   - 返回 `TokenVO {accessToken, refreshToken, expiresIn: 7200, userVO}`
5. 前端存储 accessToken 至 localStorage
6. 后续请求通过 Axios 拦截器在 `Authorization: Bearer {token}` 头中携带

**Token 刷新**（`POST /system/auth/refresh`）：
1. 前端用 refreshToken 调用
2. `JwtUtil.parseToken(refreshToken)` → 验证签名、未过期
3. 生成新 tokenId + 新 accessToken + 新 refreshToken
4. Redis 缓存新 tokenId，不失效旧的（旧 token 自然过期）

**登出**（`POST /system/auth/logout`）：
1. `JwtUtil.parseToken(token)` → 获取 tokenId 和过期时间
2. 计算剩余有效期 `expireMs = exp.getTime() - now`
3. Redis 写入 `token:blacklist:{tokenId}` → "1"（TTL = max(expireMs, 1000ms)）
4. JwtAuthFilter 验证时额外检查黑名单（TODO：当前实现未检查黑名单，应补充）

**JWT 验证链**：
```
Gateway 路由
  → System-Service JwtAuthFilter (OncePerRequestFilter)
    → 提取 Authorization header → "Bearer {token}"
    → JwtUtil.parseToken(token) → Claims
    → JwtUtil.isExpired(claims) → 未过期
    → 获取 userId, tenantId → 构造 UsernamePasswordAuthenticationToken
    → auth.setDetails(tenantId)  (tenant_id 通过此处透传)
    → SecurityContextHolder.setAuthentication(auth)
    → filterChain.doFilter() 放行
```

### 1.b 出差申请提交流程

**时序描述**（参与者：Frontend → Gateway → Expense-Service → System-Service(Feign) → Approval-Service(Feign) → Flowable）

```
前端: POST /expense/travel/{id}/submit
  → Expense Controller: TravelRequestController.submit(id)
  → Expense Service: TravelRequestServiceImpl.submit(id)
      1. 查询 ExTravelRequest，校验 status == "DRAFT"
      2. Feign → SystemFeignClient.getUser(applicantId)
         → 获取申请人姓名（失败时默认 "未知"）
      3. 构造 ApprovalStartDTO:
         {
           businessType: "TRAVEL_REQUEST",
           businessId: t.id,
           requestNo: t.requestNo,
           applicantId: t.applicantId,
           applicantName: user.getRealName(),
           amount: t.estimatedAmount,
           departmentId: t.departmentId
         }
      4. Feign → ApprovalFeignClient.startApproval(dto)
         ├── 成功: 解析 processInstanceId = response.data.processInstanceId
         └── 失败: processInstanceId = "fallback-pi-{uuid12}"  (FallbackFactory 降级)
      5. 更新 t.status = "APPROVING", t.processInstanceId = processInstanceId
      6. travelMapper.updateById(t)
  → 返回 TravelRequestVO (status=APPROVING, processInstanceId=xxx)
```

**Approval-Service 内部处理**（`ApprovalProcessServiceImpl.startProcess()`）：
1. `DroolsRuleService.evaluate("TRAVEL_REQUEST", amount)`
   - amount > 5000 → needDirector=true, 否则 false
2. switch(businessType) → processDefKey = "travel-request-approval"
3. 构造 Flowable 流程变量 Map:
   - businessType, businessId, requestNo, applicantId, applicantName, amount, needDirector, departmentId
4. `runtimeService.startProcessInstanceByKey(processDefKey, variables)`
5. 返回 ProcessStartResponse {processInstanceId, approvalLevel, warnings}

**BPMN 流转决策**：
- 流程变量 `needDirector` 由 Drools 计算，写入流程实例
- BPMN 中 GW DirectorCheck 排他网关根据 `${needDirector == true}` 决定是否进入总监审批节点
- 这确保了审批流程的节点数量是动态的

### 1.c 审批流转流程

**时序描述**（参与者：Frontend → Approval-Service → Flowable → Expense-Service(Feign) → RabbitMQ）

```
阶段1: 查询待办
  GET /approval/task/page?candidateGroup=manager
    → ApprovalTaskController.page(candidateGroup)
    → ApprovalTaskServiceImpl.listTasks(candidateGroup)
      → taskService.createTaskQuery().taskCandidateGroup("manager").list()
      → 遍历 Task → 提取 taskService.getVariables(taskId)
        填充 ApprovalTaskVO {taskId, taskName, businessType, businessId, requestNo, applicantId, applicantName}

阶段2: 完成任务
  POST /approval/task/{taskId}/complete {action: "APPROVE", comment: "同意报销"}
    → ApprovalTaskController.complete(taskId, dto)
    → ApprovalTaskServiceImpl.completeTask(taskId, approverId, null, dto)
      1. taskService.createTaskQuery().taskId(taskId).singleResult()
         → 任务不存在抛异常
      2. 如果 task.assignee == null → taskService.claim(taskId, approverId)
      3. 写入 ApApprovalRecord 审批记录:
         - businessType, businessId (取自流程变量)
         - processInstanceId, taskId, taskName
         - approverId, approverName
         - action (APPROVE/REJECT), comment
         - actionTime = now, tenantId (取自 SecurityContext)
         → recordMapper.insert(record)
      4. 映射 action → BPMN outcome:
         "APPROVE" → "APPROVED"
         "REJECT" → "REJECTED"
      5. taskService.complete(taskId, {"outcome": "APPROVED"})
```

**Flowable 引擎流转**：
- taskService.complete() 后 Flowable 计算下一步
- 排他网关根据 outcome 变量路由：
  - outcome == "REJECTED" → 走向 rejectedEnd
  - 默认 → 走向 approvedEnd 或下一步 userTask

```
阶段3: 流程结束回调
  流程到达 EndEvent (approvedEnd 或 rejectedEnd)
    → ExecutionListener: ApprovalCallbackDelegate.notify(execution)
      1. 提取变量: businessType, businessId, outcome (null→默认 APPROVED)
      2. 构造 ApprovalCallbackDTO
      3. ExpenseFeignClient.updateApprovalResult(callback)
        → PUT /expense/callback/approval-result
          → ApprovalCallbackController.updateApprovalResult(body)
            a. 根据 businessType 路由:
               TRAVEL_REQUEST → travelMapper.selectById → 更新 status
               EXPENSE_REPORT  → reportMapper.selectById → 更新 status
            b. outcome 映射:
               "REJECTED" → status = "REJECTED"
               else       → status = "APPROVED"
            c. 发送 RabbitMQ 事件:
               rabbitTemplate.convertAndSend(
                 "expense.exchange",
                 "expense.result.notified",
                 {eventId: uuid, eventType: "approval.result",
                  businessType, businessId, outcome, tenantId, applicantId}
               )
```

**委派操作**（`POST /approval/task/{taskId}/delegate?delegateToUser=xxx`）：
1. 写入 ApApprovalRecord (action="DELEGATE", comment="委派给 xxx")
2. `taskService.delegateTask(taskId, delegateToUser)` → Flowable 将任务委派给目标用户

### 1.d 报销单提交流程

**完整时序**：

```
前端: POST /expense/report/{id}/submit

ExpenseReportServiceImpl.submit(id):
  1. 查询 ExExpenseReport，校验 status == "DRAFT"
  2. 统计明细数量: itemCount >= 1（否则 400 错误）
  3. 加载明细列表，汇总金额: total = sum(item.amount)
  4. 更新 report.totalAmount = total
  5. 政策校验（非阻塞）:
     PolicyValidator.validate(items)
       → 查询 ex_expense_policy 表 (status=1)
       → 按 expenseType 匹配，检查 amount > maxAmount
       → 收集警告信息列表（仅日志，不阻断提交流程）
  6. Feign → SystemFeignClient.getUser(applicantId) 获取姓名
  7. 构造 ApprovalStartDTO {businessType: "EXPENSE_REPORT", ...}
  8. Feign → ApprovalFeignClient.startApproval(dto)
     → Approval-Service 启动 expense-report-approval 流程
     → 返回 ProcessStartResponse {processInstanceId}
  9. 更新 status = "APPROVING", processInstanceId
  10. RabbitMQ 发送 AI 审单消息:
      rabbitTemplate.convertAndSend(
        "expense.exchange",
        "expense.report.submitted",
        {eventId: uuid, reportId: r.id, amount: total, tenantId: 0}
      )
  11. 返回 ExpenseReportVO
```

**政策校验例外处理**：
- 政策超标的警告通过 `log.warn` 记录
- 不阻断提交流程（审批环节由人工判断）
- 前端可在提交前调用 `GET /expense/policy/list` 做预检查

### 1.e AI 审单流程

**异步消费流程**：

```
expense-service 发送 RabbitMQ 消息
  Exchange: expense.exchange
  Routing Key: expense.report.submitted
  → ai.review.queue

ai-service RabbitMQConsumer.onReportSubmitted(Map):
  1. 解析消息: reportId, amount, tenantId
  2. 构造 ReviewRequestDTO:
     {businessType: "EXPENSE_REPORT", businessId: reportId, totalAmount: amount}
  3. DeepSeekReviewService.review(dto, tenantId):

     a. 创建 AiReviewResult (model="deepseek-chat")
     b. buildReviewPrompt(dto):
        ┌─────────────────────────────────────────────┐
        │ 你是一个企业差旅报销审核专家。请审核以下报销申请：
        │
        │ 业务类型：EXPENSE_REPORT
        │ 报销总金额：8500.00元
        │ 明细：
        │   - 住宿费: 420.00元
        │   - 交通费: 8080.00元
        │
        │ 请输出审核结果
        │ （格式：结果:{APPROVED|REVIEW_NEEDED|REJECTED}
        │  风险等级:{LOW|MEDIUM|HIGH} 意见:<审核意见>）
        └─────────────────────────────────────────────┘

     c. chatModel.generate(prompt) → 调用 DeepSeek API
        ├── 成功: response = "结果:APPROVED 风险等级:LOW 意见:..."
        └── 异常: response = mockReviewResponse(dto)
              - amount > 10000 → REVIEW_NEEDED, MEDIUM
              - amount > 5000  → APPROVED, LOW
              - else           → APPROVED, LOW (小额自动通过)

     d. 解析响应:
        extractResult(response)     → "APPROVED" | "REVIEW_NEEDED" | "REJECTED"
        extractRiskLevel(response)  → "LOW" | "MEDIUM" | "HIGH"

     e. 设置 result 字段:
        reviewResult = extractResult(...)
        riskLevel = extractRiskLevel(...)
        reviewOpinion = response (原始文本)
        confidence = 0.88 (固定值，生产应从 API 获取)
        processTimeMs = end - start

     f. reviewMapper.insert(result) → 持久化审单结果
```

**审单结果实体** (`AiReviewResult`)：

| 字段 | 说明 |
|---|---|
| businessType | 业务类型 |
| businessId | 关联业务 ID |
| model | 模型名称 (deepseek-chat) |
| reviewResult | APPROVED / REVIEW_NEEDED / REJECTED |
| riskLevel | LOW / MEDIUM / HIGH |
| reviewOpinion | 完整 AI 审核意见文本 |
| confidence | 置信度 |
| processTimeMs | 处理耗时(ms) |

**同步审单 API**（`POST /ai/review/evaluate`）：
- 流程同上但为同步调用，前端可直接调用获取审单结果
- 适用于审批工作台手动触发审单

**风险分析 API**（`POST /ai/review/risk`）：
- 当前为占位实现，返回固定值 `riskLevel=LOW`
- 生产应读取 AiReviewResult + 历史数据做风控分析

### 1.f 通知推送流程

```
expense-service 发送 RabbitMQ 消息
  Exchange: expense.exchange
  Routing Key: expense.result.notified
  → notification.event.queue

notification-service RabbitMQConsumer.onNotificationEvent(Map):
  1. 提取字段:
     eventType, businessType, businessId, tenantId, applicantId, outcome, message
  2. 生成标题和内容:
     outcome == "APPROVED"  → title="审批结果通知", content="您的{businessType}已通过"
     outcome == "REJECTED"  → title="审批结果通知", content="您的{businessType}已驳回"
     其他                   → title="系统通知",    content=message 或 "您有一条新的通知"
  3. 双通道推送:

     通道A: 站内消息
       MessageServiceImpl.send(userId, title, content, "NOTIFICATION", businessType, businessId, tenantId)
         → 构造 NtMessage {tenantId, userId, title, content, messageType, businessType, businessId, isRead=0}
         → messageMapper.insert(msg)
         → 前端通过 GET /notification/message/page 查看

     通道B: 钉钉机器人
       DingTalkService.send(title, content)
         ├── mock=true  → log.info("[钉钉 Mock] 发送消息: title={}, content={}")
         ├── mock=false + webhookUrl 有效:
         │     → HMAC-SHA256 加签
         │     → POST webhookUrl (JSON body, Markdown 格式)
         │     → 检查 HTTP 200 响应
         └── mock=false + webhookUrl 空: log.warn 跳过
  4. 异常处理: try-catch 包裹全流程，异常仅记录日志不抛出
     （确保单条消息失败不影响后续消费）
```

**消息去重**：
- 每条消息携带 `eventId`（UUID v4），由发送端生成
- 消费端可通过 eventId 在 Redis 中记录已处理事件，实现幂等消费（当前为设计预留，代码中已包含 eventId 字段但在消息体中被消费端用于字符串拼接，暂未做去重逻辑）

---

## 2. 状态机设计

### 2.1 出差申请状态流转 (ExTravelRequest)

```
                    ┌─────────┐
                    │  DRAFT  │  (草稿)
                    └────┬────┘
                         │ submit()
                    ┌────▼────────┐
                    │  APPROVING  │  (审批中)
                    └────┬────────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
         REJECTED    APPROVED   withdraw()
              │          │          │
              │          │     ┌────▼────────┐
              │          │     │  WITHDRAWN  │  (已撤回)
              │          │     └─────────────┘
              │          │
              └──────────┴── withdraw()
                      │
                 ┌────▼────────┐
                 │  WITHDRAWN  │
                 └─────────────┘
```

**状态枚举**：
| 状态 | 触发条件 | 可执行操作 |
|---|---|---|
| DRAFT | 创建/编辑后重置 | edit, delete, submit |
| APPROVING | submit() 成功后 | withdraw |
| APPROVED | 审批回调 outcome=APPROVED | withdraw |
| REJECTED | 审批回调 outcome=REJECTED | (终态，不可再操作) |
| WITHDRAWN | withdraw() | edit（编辑后回到 DRAFT） |

**代码约束**（`TravelRequestServiceImpl`）：
- `submit()`: 仅 DRAFT → APPROVING
- `withdraw()`: SUBMITTED / APPROVING / APPROVED → WITHDRAWN
- `update()`: DRAFT / WITHDRAWN → DRAFT (编辑后重置为草稿)
- `delete()`: 仅 DRAFT 可删除

### 2.2 报销单状态流转 (ExExpenseReport)

```
                    ┌─────────┐
                    │  DRAFT  │  (草稿，可添加明细)
                    └────┬────┘
                         │ submit()
                         │ ├── 校验明细 >= 1
                         │ ├── 政策校验 PolicyValidator
                         │ ├── Feign 启动审批
                         │ └── RabbitMQ AI 审单
                    ┌────▼────────┐
                    │  APPROVING  │  (审批中)
                    └────┬────────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
         REJECTED    APPROVED   withdraw()
              │          │          │
              │          │     ┌────▼────────┐
              │          │     │  WITHDRAWN  │
              │          │     └─────────────┘
              │          │
              │          └─── 财务打款 ───► PAID
              │                            (打款完成)
              │
              └────────── withdraw()
                      │
                 ┌────▼────────┐
                 │  WITHDRAWN  │
                 └─────────────┘
```

**状态枚举**：
| 状态 | 触发条件 | 可执行操作 |
|---|---|---|
| DRAFT | 创建/编辑后 | edit, delete, addItem, submit |
| APPROVING | submit() 成功 | withdraw |
| APPROVED | 审批回调 outcome=APPROVED | withdraw, 触发打款流程 |
| REJECTED | 审批回调 outcome=REJECTED | (终态) |
| WITHDRAWN | withdraw() | edit（编辑后回到 DRAFT） |
| PAID | 财务打款完成 | (终态) |

**代码约束**（`ExpenseReportServiceImpl`）：
- `submit()`: 仅 DRAFT → APPROVING；至少 1 条明细
- `withdraw()`: SUBMITTED / APPROVING / APPROVED → WITHDRAWN
- `update()`: 仅 DRAFT 可编辑
- `delete()`: 仅 DRAFT 可删除；级联删除明细
- `addItem()`: 仅 DRAFT 可添加明细

**状态流转与审批流程的对应**：
```
出差申请: BPMN 决策 needDirector
  DRAFT → submit → APPROVING → BPMN 流转
    → (needDirector=false) 经理通过 → APPROVED
    → (needDirector=true)  经理通过 → 总监审批 → 总监通过 → APPROVED
    → 任一节点驳回 → REJECTED

报销单: BPMN 两层审批
  DRAFT → submit → APPROVING → BPMN 流转
    → 财务通过 → 经理通过 → APPROVED
    → 任一节点驳回 → REJECTED
```

---

## 3. 异常处理设计

### 3.1 Feign 降级策略

**设计原则**：所有跨服务 Feign 调用必须配置 `fallbackFactory`，服务不可用时返回降级数据，保证调用方核心流程不中断。

**已实现的 Fallback**：

#### 3.1.1 expense-service → approval-service (ApprovalFeignFallbackFactory)

```java
// 触发条件: approval-service 不可用 / 超时 / 返回异常
// 降级行为: 返回一个标记为 fallback 的 ProcessStartResponse
ApprovalProcessStartResponse resp = new ApprovalProcessStartResponse();
resp.setProcessInstanceId("fallback-pi-" + UUID.substring(0,12));
resp.setApprovalLevel("SINGLE");
resp.setWarnings(Collections.emptyList());
return Result.ok(resp);
```

**影响分析**：
- 出差申请/报销单仍可提交，状态变为 APPROVING
- 但 `processInstanceId` 为 fallback 标识，审批流程实际未创建
- 后续人工审批无法走 Flowable 流转，需运维介入手动处理

#### 3.1.2 expense-service → system-service (SystemFeignFallbackFactory)

```java
// 触发条件: system-service 不可用
// 降级行为: 用户名/部门名返回 "未知"
// 在 TravelRequestServiceImpl.toVO() / ExpenseReportServiceImpl.toVO() 中
//   try { Feign 调用 } catch (Exception ignored) { 设置默认值 "未知" }
```

**影响分析**：仅影响前端展示（姓名/部门显示为"未知"），不影响核心业务流转。

#### 3.1.3 approval-service → expense-service (ExpenseFeignFallbackFactory)

```java
// 触发条件: expense-service 不可用（审批回调失败）
// 降级行为: ApprovalCallbackDelegate.notify() 中 try-catch
//   log.error("审批结果回写失败: businessId={}, error={}", businessId, e.getMessage())
```

**影响分析**：
- 审批流程在 Flowable 中正常完成
- 但 expense-service 的状态未更新（保持 APPROVING）
- 此时需依赖重试机制或人工补偿

### 3.2 Sentinel 限流

**配置位置**：`GatewayConfig.initSentinelBlockHandler()`

**限流规则**：
- 粒度：每用户 100 req/s
- 触发后返回：
```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试",
  "data": null
}
```

**限流不会导致服务崩溃**：前端应捕获 429 状态码，显示提示并引导用户稍后重试。

### 3.3 消息异常处理

#### 3.3.1 消息发送异常

```java
// expense-service ApprovalCallbackController:
try {
    rabbitTemplate.convertAndSend("expense.exchange", "expense.result.notified", event);
} catch (Exception e) {
    log.error("RabbitMQ 消息发送失败", e);
    // 不抛异常，不阻断审批状态更新
}

// expense-service ExpenseReportServiceImpl.submit():
try {
    rabbitTemplate.convertAndSend("expense.exchange", "expense.report.submitted", event);
} catch (Exception ignored) {
    // AI 审单异步消息发送失败，不阻断报销单提交
}
```

#### 3.3.2 消息消费异常

```java
// notification-service RabbitMQConsumer:
try {
    messageService.send(...);
    dingTalkService.send(...);
} catch (Exception e) {
    log.error("通知事件处理失败", e);
    // 不抛异常，不触发 RabbitMQ 重试（避免死循环）
}

// ai-service RabbitMQConsumer:
try {
    reviewService.review(dto, tenantId);
} catch (Exception e) {
    log.error("AI 审单消息处理失败", e);
    // 不抛异常
}
```

**重要**：当前消费端未显式配置死信队列（DLQ）。生产环境建议：
1. 配置 `auto-bind-dlq: true` 或手动声明 DLX/DLQ
2. 消费端区分可重试异常（临时网络故障）和不可重试异常（数据格式错误）
3. 不可重试的消息路由到 DLQ，由运维手动处理

### 3.4 Drools 引擎容错

```
DroolsConfig.kieContainer():
  try {
    // 加载 classpath:rules/*.drl
    // 编译规则
  } catch (Exception e) {
    return null;  // 降级标志
  }

DroolsRuleService (构造注入 @Autowired(required=false)):
  if (kieContainer != null) {
    // KieSession → 插入 fact → fireAllRules
  } else {
    // Java fallback: 等价规则逻辑
  }
```

**触发场景**：Drools 9.x 版本兼容性问题（KIE API 变化、类路径冲突等），确保规则引擎失败不影响审批流程启动。

### 3.5 DeepSeek API 调用容错

```java
// DeepSeekReviewServiceImpl.review():
try {
    response = chatModel.generate(prompt);
} catch (Exception e) {
    log.error("DeepSeek API 调用失败，使用 Mock 审单结果", e);
    response = mockReviewResponse(dto);
}
```

**Mock 降级逻辑**：
- 金额 > 10000 → REVIEW_NEEDED, MEDIUM
- 金额 > 5000 → APPROVED, LOW
- 其他 → APPROVED, LOW

**RagServiceImpl.ask()** 同样含降级：
```java
catch (Exception e) {
    answer = "抱歉，AI 服务暂时不可用，请稍后重试。您也可以查阅公司差旅费用政策手册。";
}
```

### 3.6 钉钉 Webhook 容错

```java
// DingTalkService.send():
if (mock) → 仅日志输出
if (webhookUrl 为空) → log.warn 跳过
try {
    HttpClient POST → 检查响应
} catch (Exception e) {
    log.error("[钉钉] 消息发送异常: {}", e.getMessage());
}
// 不抛异常，不影响站内消息的发送
```

---

## 4. 配置管理

### 4.1 Nacos 配置中心

**注册发现**：所有微服务通过 `spring.cloud.nacos.discovery.server-addr` 注册到 Nacos。

**配置中心使用说明**：

```yaml
# bootstrap.yml / application.yml (各服务)
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yaml
        shared-configs:
          - data-id: expenseflow-common.yaml
            group: DEFAULT_GROUP
            refresh: true
```

**配置分层**：
| 配置层级 | Data ID | 说明 |
|---|---|---|
| 共享配置 | `expenseflow-common.yaml` | 所有服务共享：数据库连接、Redis、RabbitMQ |
| 服务专属 | `{service-name}.yaml` | 各服务特有配置 |
| 环境变量 | Docker Compose / K8s ConfigMap | 敏感信息（密码、API Key） |

**关键配置项**：

#### 数据库（共享配置）
```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/expenseflow?useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### Redis（共享配置）
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:}
```

#### RabbitMQ（共享配置）
```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    listener:
      simple:
        acknowledge-mode: auto
        retry:
          enabled: false  # 当前关闭重试，避免消费失败死循环
```

#### DeepSeek API（ai-service）
```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY:sk-default}
      model-name: deepseek-chat
```

#### 钉钉机器人（notification-service）
```yaml
dingtalk:
  mock: ${DINGTALK_MOCK:true}
  webhook-url: ${DINGTALK_WEBHOOK_URL:}
  secret: ${DINGTALK_SECRET:}
```

#### JWT（system-service / expense-common）
```yaml
jwt:
  secret: ${JWT_SECRET:ExpenseFlowSecretKeyForJWT2024}
  access-token-expiration: 7200
  refresh-token-expiration: 604800
```

#### Sentinel（gateway-service）
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8080}
      eager: true
```

### 4.2 多租户配置

**隔离策略**：共享数据库 + `tenant_id` 列隔离。

**配置类**：`MybatisPlusConfig` 注册 `ExpenseFlowTenantLineHandler`

**忽略多租户的表**（全局数据）：
- `sys_tenant` — 租户定义表自身
- `sys_permission` — 权限全局共享
- `sys_user_role` — 用户角色关联
- `sys_role_permission` — 角色权限关联

**请求传递**：
```
前端: Header "X-Tenant-Id: {tenantId}"
  → TenantContextFilter (Filter, Order(1)):
    提取 X-Tenant-Id → ExpenseFlowTenantLineHandler.setTenant(tenantId)
    → 请求结束后 finally clear()
  → MyBatis-Plus 自动在 SQL WHERE 条件追加 "AND tenant_id = ?"
```

### 4.3 环境变量参考（Docker Compose）

```bash
# 数据库
MYSQL_HOST=mysql
MYSQL_USER=root
MYSQL_PASSWORD=root123

# Redis
REDIS_HOST=redis

# RabbitMQ
RABBITMQ_HOST=rabbitmq
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# Nacos
NACOS_ADDR=nacos:8848

# AI
DEEPSEEK_API_KEY=sk-xxxx

# 钉钉
DINGTALK_MOCK=true
DINGTALK_WEBHOOK_URL=
DINGTALK_SECRET=

# JWT
JWT_SECRET=ExpenseFlowSecretKeyForJWT2024
```

**配置优先级**：环境变量 > Nacos 配置中心 > application.yml 默认值。

---

## 附录

### A. 数据库表关系（核心业务）

```
ex_travel_request (出差申请)
  ├── id ← ex_expense_report.travel_request_id (报销单关联)
  └── id → approval callback (businessType=TRAVEL_REQUEST)

ex_expense_report (报销单)
  ├── id ← ex_expense_item.report_id (报销明细)
  ├── id ← ex_invoice.report_id (发票关联)
  └── id → approval callback (businessType=EXPENSE_REPORT)

ex_expense_policy (费用政策)
  └── expenseType → PolicyValidator 按类型匹配上限

ap_approval_record (审批记录)
  └── businessType + businessId → 关联出差申请或报销单

ai_ocr_result (OCR 识别结果)
  └── invoiceId → 关联发票

ai_review_result (AI 审单结果)
  └── businessId → 关联报销单

nt_message (站内消息)
  └── businessType + businessId → 关联业务单据

nt_notification_template (消息模板)
  └── 独立管理，按 templateCode 引用
```

### B. 关键枚举值汇总

| 领域 | 字段 | 可选值 |
|---|---|---|
| 出差申请 | status | DRAFT, APPROVING, APPROVED, REJECTED, WITHDRAWN |
| 报销单 | status | DRAFT, APPROVING, APPROVED, REJECTED, WITHDRAWN, PAID |
| 审批 | action | APPROVE, REJECT, DELEGATE |
| 审批 | outcome (BPMN) | APPROVED, REJECTED |
| OCR | status | PROCESSING, SUCCESS, FAILED |
| AI 审单 | reviewResult | APPROVED, REVIEW_NEEDED, REJECTED |
| AI 审单 | riskLevel | LOW, MEDIUM, HIGH |
| 消息模板 | channel | IN_APP, DINGTALK |
| 消息模板 | status | 0=禁用, 1=启用 |
| 消息 | messageType | NOTIFICATION, WARNING, ERROR |
| 消息 | isRead | 0=未读, 1=已读 |
