# 生产就绪标准化设计（M8收尾 + M9测试 + M10生产就绪）

> 版本：v1.0 | 日期：2026-05-15 | 状态：待实施

## 目标

全部核心链路达到生产级质量，全服务 Service 层单元测试 ≥60% 覆盖率。范围：

- **M8 收尾**（1-2天）：安全加固 + 编号修复 + 可观测性基础
- **M9 测试专项**（3-4天）：6 服务 Service 层单元测试 + Controller 集成测试
- **M10 生产就绪**（2-3天）：消息可靠性 + 限流 + CI + 代码质量

---

## M8 收尾

### 1. JWT Token 黑名单检查

**现状**：Gateway 的 `JwtAuthGatewayFilter` 已检查 Redis 黑名单，但 system-service 的 `JwtAuthFilter` 未检查。若请求绕过 Gateway 直连 system-service，logout 后的 Token 仍可通过。

**改动**：`system-service/.../JwtAuthFilter.java`

- 注入 `RedisTemplate<String, String>`
- 解析 Token 后、设置 SecurityContext 前，增加 `redisTemplate.hasKey("token:blacklist:" + tokenId)`
- 命中黑名单：不设置认证，直接放行（后续 `@PreAuthorize` 拦截返回 403）

**验证**：logout 后携带旧 Token 访问受保护接口，预期 403。

### 2. 前端路由权限守卫

**现状**：`router.beforeEach` 仅检查 token 存在性，不区分角色。任何已登录用户可访问所有页面。

**改动**：

`expense-web/src/utils/jwt.ts`（新增）：
```typescript
// 解析 JWT payload 获取 roles（不需要验证签名，Gateway/后端已做）
export function getUserRoles(): string[] {
  const token = localStorage.getItem('token')
  if (!token) return []
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.roles || []
  } catch { return [] }
}
```

`expense-web/src/router/index.ts`（修改）：
- 路由 meta 增加 `roles` 字段
- `beforeEach` 增加角色校验：角色不匹配 → `/dashboard` + ElMessage 提示"无权限"
- `/login` 成功后在 Pinia store 存角色列表（避免重复解析 JWT）

路由角色映射：
| 路由 | 允许角色 |
|------|---------|
| /dashboard | 所有登录用户 |
| /travel, /report, /invoice | 所有登录用户 |
| /approval | APPROVER, FINANCE, SUPER_ADMIN |
| /ai-review, /ai-assistant | FINANCE, APPROVER, SUPER_ADMIN |
| /notification | 所有登录用户 |

### 3. NoGenerator 编号生成修复

**现状**：`System.currentTimeMillis() % 10000` 做序列号，并发下可能重复，且不保证递增。

**改动**：`expense-service/.../NoGenerator.java`

- 引入 `AtomicLong counter` + `volatile String currentDate`
- 日期变更时同步重置计数器
- 序列号范围 0000-9999，单租户单日足够

### 4. Actuator 健康检查 + 统一日志格式

**Actuator**：6 个服务 `application.yml` 暴露 `health,info` 端点。Gateway 额外暴露 `gateway` 端点。

**日志**：各服务 `logback-spring.xml` 统一配置：
- 控制台：文本格式（开发友好，保留彩色输出）
- 文件：JSON 格式（`logstash-logback-encoder`），输出到 `logs/{service}.json`
- 日志级别：默认 INFO，`com.expenseflow` 为 DEBUG

---

## M9 测试专项

### 测试策略

| 层 | 工具 | 目标 | 指标 |
|---|------|------|:---:|
| Service 单元测试 | JUnit 5 + Mockito + MockitoExtension | 所有 Service 实现类 | ≥60% 行覆盖 |
| Controller 集成测试 | @SpringBootTest + @AutoConfigureMockMvc + H2 | 每个 Controller 2-3 个核心接口 | 核心接口必测 |
| Mapper 测试 | @MybatisPlusTest + H2 | 仅复杂 SQL（多表联查） | 按需 |

### 基础设施

- 各服务 `src/test/resources/application-test.yml`：H2 内存数据库 + 禁用 Nacos/RabbitMQ/Redis（测试用 Mock）
- expense-common `src/test` 目录提供 `BaseServiceTest`（Mockito 注解初始化）
- 测试类命名：`{被测类名}Test`
- 测试方法命名：`should{预期}When{条件}` + `@DisplayName("中文描述")`

### 各服务测试清单

#### system-service（4 Service）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| AuthServiceImpl | login成功/密码错误/用户禁用、logout黑名单、refresh token | 8 |
| UserServiceImpl | 分页查询、创建、更新、删除、角色分配 | 6 |
| RoleServiceImpl | 创建、删除、权限关联/去关联 | 5 |
| TenantServiceImpl | 创建、状态切换（启用/禁用） | 4 |

#### expense-service（5 Service，最重）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| TravelRequestServiceImpl | submit(DRAFT→APPROVING)、withdraw(多状态)、状态校验、金额超限拒绝 | 10 |
| ExpenseReportServiceImpl | submit(含明细>=1)、withdraw、addItem、delete、政策校验 | 10 |
| ExpensePolicyService | CRUD、启用/禁用、按类型查询 | 6 |
| PaymentService | 创建打款、流水号生成、重复创建拒绝 | 5 |
| InvoiceService | 上传、关联报销单、OCR 触发 | 5 |

#### approval-service（3 Service）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| ApprovalProcessServiceImpl | startProcess(Drools评估→Flowable启动)、callback(TRAVEL_REQUEST/EXPENSE_REPORT) | 8 |
| ApprovalTaskServiceImpl | listTasks(候选组)、complete(通过/驳回)、delegate | 8 |
| DroolsRuleService | needDirector(>5000)、不需要总监(<5000)、Drools降级fallback | 5 |

#### ai-service（3 Service）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| DeepSeekReviewService | API正常返回、API异常降级、金额阈值(>10000/>5000/<=5000) | 7 |
| OcrService | 识别成功(PROCESSING→SUCCESS)、失败(PROCESSING→FAILED) | 4 |
| RagService | 政策问答成功、API异常降级文案 | 4 |

#### notification-service（3 Service）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| MessageServiceImpl | 发送(不重复)、分页、标已读、批量标已读 | 7 |
| DingTalkService | mock模式仅日志、真实发送HTTP 200、webhook为空跳过、HTTP异常 | 6 |
| TemplateService | 按code查询、创建、更新、删除 | 5 |

#### gateway-service（无 Service 层）

| 被测类 | 关键测试场景 | 预估方法数 |
|--------|------------|:---:|
| JwtAuthGatewayFilter | Token有效→转发+headers、Token过期→401、无Token白名单路径→放行、黑名单Token→403、Swagger路径→放行 | 5 |

**总计预估**：40-50 个测试类，150-180 个测试方法。

---

## M10 生产就绪

### 1. RabbitMQ 死信队列

为两个核心队列配置 DLX/DLQ：

```
ai.review.queue → (x-dead-letter-exchange: ai.review.dlx)
  → ai.review.dlx → ai.review.dlq

notification.event.queue → (x-dead-letter-exchange: notification.event.dlx)
  → notification.event.dlx → notification.event.dlq
```

- 消费失败不重试（当前配置保持 `retry.enabled=false`），直接入 DLQ
- DLQ 消息保留 7 天，运维可手动重投
- 改动：ai-service + notification-service 的 `RabbitConfig.java`

### 2. 消息幂等消费

- 消费端处理前，用 eventId 在 Redis 中 SETNX 检查
- Key: `event:consumed:{eventId}`，TTL 86400s（24h）
- 已处理 → 直接 ACK，不执行业务逻辑
- 改动：ai-service + notification-service 的消费者类

### 3. Sentinel 限流规则细化

| 路径 | 规则 | 原因 |
|------|------|------|
| `/system/auth/login` | 10 req/s/user | 防暴力破解 |
| `/expense/report/*/submit` | 5 req/s/user | 提交操作重业务 |
| `/approval/task/*/complete` | 10 req/s/user | 审批频次低 |
| `/ai/review/*` | 3 req/s/user | DeepSeek API 频率限制 |
| 其他 | 100 req/s/user（保持不变） | 全局兜底 |

配置方式：Gateway `application.yml` 声明 Sentinel 规则（或通过 Nacos 动态下发）。

### 4. CI 配置

`.github/workflows/ci.yml`：

```yaml
name: CI
on:
  push:
    branches: [master, develop, 'feature/**']
  pull_request:
    branches: [master, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      mysql: ...
      redis: ...
      rabbitmq: ...
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4 (JDK 17)
      - name: Cache Maven (actions/cache)
      - run: mvn compile -DskipTests
      - run: mvn test
      - run: mvn spotless:check
```

### 5. 代码格式化

- Java：已有 spotless-maven-plugin，运行 `mvn spotless:apply` 统一格式化
- 前端：已有 Prettier，补充 `.prettierrc` 配置
- CI 中 `spotless:check` 作为质量门，不通过则 CI 失败
- 不配置 pre-commit hook（避免因环境差异导致提交失败）

### 6. 全链路验收清单

| # | 检查项 | 验证方法 |
|---|--------|---------|
| 1 | 不同角色登录后菜单可见性不同 | 分别用 admin/manager/finance 账号登录检查 |
| 2 | logout 后旧 Token 被拒绝 | 登出后用旧 Token 调用接口 |
| 3 | 出差申请完整流转 | DRAFT → submit → 经理审批 → 总监审批 → APPROVED |
| 4 | 审批驳回后状态回写 | 审批驳回 → expense-service 状态变 REJECTED |
| 5 | AI 审单降级 | 关闭 DeepSeek API Key → 验证 Mock 降级结果 |
| 6 | 通知双通道 | 审批结果 → 站内消息已读 + 钉钉 webhook |
| 7 | 消息幂等 | 手动发重复 eventId → 验证 SETNX 拦截 |
| 8 | DLQ | 消费端抛异常 → 消息进入 DLQ |
| 9 | Sentinel 限流 | 高频调用 `/system/auth/login` → 429 |
| 10 | 健康检查 | `GET /actuator/health` 全部返回 UP |
| 11 | 单元测试 | `mvn test` 全部通过，覆盖率 ≥ 60% |
| 12 | CI 通过 | GitHub Actions 全绿 |

---

## 分支与提交策略

```
feature/m5-ai-notification (当前) → M8 收尾
feature/m9-testing (从 M8 合入 master 后创建) → M9 测试
feature/m10-production-ready (从 M9 合入后创建) → M10 生产就绪
```

每个里程碑完成后合入 master，下个里程碑从 master 拉新分支。

---

## 不在此次范围的事项

以下事项明确标记为"未来规划"，不在 M8/M9/M10 中实现：

- 性能测试（JMeter/Gatling）
- Kubernetes 部署配置
- 多语言国际化（i18n）
- ELK/Grafana Loki 日志平台搭建
- 数据备份与恢复方案
- 业务编号全局唯一（Redis 自增方案替代当前 AtomicLong）
