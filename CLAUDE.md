# CLAUDE.md — ExpenseFlow 差旅报销智能管理平台

## 项目概述

基于 Spring Boot 3.3 + Spring Cloud 2023 + Vue 3 的微服务架构，覆盖出差申请 → 消费记录 → 报销提交 → 多级审批 → 财务打款的全链路，融合 AI 能力（OCR / DeepSeek 审单 / RAG 问答 / 风控预警）。

## 技术栈速览

| 层 | 技术 |
|---|------|
| 后端框架 | Spring Boot 3.3 + Spring Cloud 2023 + JDK 17 |
| 微服务治理 | Nacos 2.3（注册发现 + 配置中心）、Spring Cloud Gateway、OpenFeign、Sentinel |
| 工作流/规则 | Flowable 7 + Drools 9 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0（共享数据库 + tenant_id 多租户隔离） |
| 缓存 | Redis 7 + Caffeine（二级缓存） |
| 消息队列 | RabbitMQ 3.13 |
| AI | LangChain4j 0.35 + DeepSeek Chat API + 阿里云 OCR SDK |
| 可观测性 | Micrometer → Prometheus → Grafana + SkyWalking 10 |
| 前端 | Vue 3.4 + TypeScript + Vite + Element Plus + ECharts |
| 部署 | Docker Compose |

## 微服务清单（6 服务）

| 服务 | 端口 | 职责 |
|------|:---:|------|
| gateway-service | 8080 | 统一网关、JWT 验签、Sentinel 限流、路由转发 |
| system-service | 8081 | 租户、用户/角色/权限(RBAC)、部门、OAuth2/SSO、多租户拦截 |
| expense-service | 8082 | 出差申请、报销单、发票管理、消费记录、打款流水、费用政策 |
| approval-service | 8083 | Flowable 工作流、Drools 规则引擎、审批操作 |
| ai-service | 8084 | OCR 识别、DeepSeek 审单、RAG 问答、风控分析 |
| notification-service | 8085 | 钉钉机器人、站内消息、RabbitMQ 消费 |

---

## 一、代码规范

### 1.1 命名规范

**Java（遵循阿里巴巴 Java 开发手册）**

```java
// ✅ 正确
public class ExpenseReportController {}      // 类：大驼峰
public interface ExpenseReportService {}      // 接口：大驼峰
public class ExpenseReportServiceImpl {}      // 实现类：接口名 + Impl
public ExpenseReport queryById(Long id) {}    // 方法：小驼峰，动词开头
private BigDecimal totalAmount;               // 变量：小驼峰
private static final int MAX_RETRY = 3;       // 常量：全大写 + 下划线

// ❌ 错误
public class expensereportcontroller {}       // 全小写
public ExpenseReport query(Long id) {}        // 方法名不够描述性
private BigDecimal total_amount;              // 蛇形命名（仅 SQL 可用）
```

**Vue/TypeScript**

```typescript
// ✅ 组件文件名：大驼峰，多单词（避免与 HTML 元素冲突）
ExpenseReportList.vue
ApprovalWorkbench.vue

// ✅ 组合式 API + <script setup lang="ts">
const reportList = ref<ExpenseReport[]>([]);
const handleSubmit = async () => {};

// ❌ 禁止 Options API（统一使用 Composition API）
```

### 1.2 包结构规范

每个微服务统一采用以下包结构：

```
com.expenseflow.{service}
├── controller/       # REST 控制器（只做参数校验、调用 Service、返回封装）
├── service/          # 业务接口
│   └── impl/         # 业务实现
├── mapper/           # MyBatis-Plus Mapper 接口
├── entity/           # 数据库实体（PO）
├── dto/              # 数据传输对象（入参/出参分离）
├── vo/               # 视图对象（前端展示专用）
├── enums/            # 枚举类
├── config/           # Spring 配置类
├── feign/            # Feign 远程调用接口
│   └── fallback/     # Feign 降级实现
└── handler/          # 处理器（多租户拦截器等）
```

### 1.3 注释规范

- **禁止** Javadoc 形式的冗余注释（"根据 ID 查询" 这种废话）
- **允许** 注释的场景：非显而易见的业务规则、临时 workaround、TODO/FIXME
- 不生成 `@author` `@date` `@since` 等模板注释
- 复杂方法写一行简要注释说明 WHY，不是 WHAT

```java
// ✅ 好的注释：解释为什么
// 金额超过阈值时需触发总监审批，规则由 Drools 执行（避免硬编码）
private String determineApprovalLevel(BigDecimal amount) { ... }

// ❌ 废话注释：代码本身已说明
/**
 * 根据金额决定审批等级
 * @param amount 金额
 * @return 审批等级
 */
```

### 1.4 格式规范

- 缩进：4 空格（Java），2 空格（Vue/TS）
- 单行长度：≤ 120 字符
- 文件末尾必须有空行
- 提交前运行 `mvn spotless:apply`（Java）或 `prettier`（前端）格式化
- Controller 只做三件事：接收参数 → 调用 Service → 返回结果。禁止在 Controller 写业务逻辑

---

## 二、Git 分支策略

### 2.1 分支模型

```
main                     # 生产就绪代码，仅通过 PR 合入
  └── develop            # 开发主线
        ├── feature/m1-* # M1 里程碑特性分支
        ├── feature/m2-*
        ├── ...
        └── feature/m7-*
```

### 2.2 命名规范

```
feature/m{里程碑}-{服务名}-{简述}    # 功能开发
fix/{简述}                           # Bug 修复
docs/{简述}                          # 文档
```

示例：
```
feature/m1-common-project-scaffold
feature/m2-system-service-auth
feature/m3-expense-crud
feature/m4-approval-flowable
fix/ocr-amount-parse-error
```

### 2.3 提交信息格式

```
<type>(<scope>): <subject>
```

type: `feat` | `fix` | `refactor` | `docs` | `test` | `chore`

示例：
```
feat(expense): 实现出差申请草稿保存与提交
fix(approval): 修复审批驳回后状态未回写
refactor(common): 抽取统一分页响应体
```

- Commit 粒度：一个逻辑变更一个 Commit
- 禁止大 Commit（"完成所有功能"）
- 每天至少一次本地 Commit，推送频率至少每天一次

---

## 三、微服务调用规范

### 3.1 同步调用（OpenFeign）

```java
// 接口定义在调用方模块的 feign/ 包下
@FeignClient(name = "approval-service", path = "/approval",
             fallbackFactory = ApprovalFeignFallback.class)
public interface ApprovalFeignClient {
    @PostMapping("/process/start")
    Result<String> startApproval(@RequestBody ApprovalStartDTO dto);
}

// 调用时
Result<String> result = approvalFeignClient.startApproval(dto);
if (!result.isSuccess()) {
    throw new BusinessException("启动审批流失败: " + result.getMessage());
}
```

规则：
- Feign 接口统一定义在调用方服务
- 必须配置 `fallbackFactory`（Sentinel 熔断降级）
- 请求/响应使用 DTO 传输，禁止直接暴露 Entity
- Feign 调用必须记录耗时日志（DEBUG 级别）

### 3.2 异步消息（RabbitMQ）

```
生产者端：
  - expense-service 提交报销单后 → 发送 "报销单已提交" 事件
  - approval-service 审批完成后 → 发送 "审批结果" 事件

消费者端：
  - notification-service 消费上述事件 → 推送钉钉通知
```

规则：
- Exchange 命名：`{业务域}.exchange`
- Queue 命名：`{服务名}.{事件}.queue`
- Routing Key：`{事件类型}`
- 消息体统一使用 JSON，包含 `eventId`（UUID，用于去重）
- 消费端做幂等处理（基于 eventId 去重）

### 3.3 多租户上下文透传

```
前端请求头: X-Tenant-Id: {tenantId}
       ↓
Gateway: 校验 + 透传至下游
       ↓
System/Expense/Approval/AI/Notification: 
  TenantLineHandler 从请求头提取 tenant_id，MyBatis-Plus 自动注入 SQL

OpenFeign 调用链:
  Feign 拦截器自动从当前 RequestContext 获取 tenant_id，写入请求头
```

### 3.4 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1706000000000,
  "requestId": "uuid"
}
```

- 成功 code=200，业务异常 code=4xx，系统异常 code=5xx
- 分页响应统一包装：`{ "code": 200, "data": { "records": [], "total": 100, "page": 1, "size": 10 } }`

---

## 四、安全规范

### 4.1 认证授权

- JWT Token 有效期：Access Token 2h，Refresh Token 7d
- Token 存储：前端 localStorage，每次请求通过 Axios 拦截器自动携带
- 登出时 Token 加入 Redis 黑名单（有效期对齐 Token 过期时间）
- RBAC 权限校验：`@PreAuthorize("@pms.hasPermission('expense:report:delete')")`
- 敏感操作（审批、打款）额外记录操作审计日志

### 4.2 数据保护

- **密码**：BCrypt 加密存储（Spring Security 默认强度 10）
- **敏感配置**：数据库密码、API Key 使用 jasypt 加密，密钥通过环境变量注入
- **数据脱敏**：手机号（138****1234）、银行卡号（仅后4位）、身份证号在 VO 层标注 `@Sensitive` 注解序列化时脱敏
- **SQL 注入**：禁止 `${}` 拼接 SQL（MyBatis-Plus 参数化查询 `#{}`）
- **文件上传**：仅允许 PNG/JPG/PDF，大小限制 10MB，存储路径不在 web 根目录

### 4.3 接口安全

- **幂等**：创建/提交类接口使用 Token 机制（先获取 token → 请求携带 token → 消费后失效）
- **CSRF**：前后端分离 + JWT 无状态，天然免疫 CSRF（不使用 Cookie）
- **CORS**：Gateway 层统一配置允许域名，禁止 `*` 通配
- **限流**：Gateway 层 Sentinel 限流，每个用户 100 req/s

---

## 五、测试要求

### 5.1 测试层级

| 层级 | 覆盖目标 | 工具 | 最低覆盖率 |
|------|---------|------|:---:|
| 单元测试 | Service 层业务逻辑 | JUnit 5 + Mockito | 60% |
| 集成测试 | Controller + DB | Spring Boot Test + H2 | 核心接口必测 |
| API 测试 | 完整 HTTP 调用链 | 手动 Postman / Bruno | 演示用 |

### 5.2 测试规范

```java
// 测试类命名：{被测类名}Test
@ExtendWith(MockitoExtension.class)
class ExpenseReportServiceImplTest {

    @Mock
    private ExpenseReportMapper reportMapper;

    @InjectMocks
    private ExpenseReportServiceImpl reportService;

    @Test
    @DisplayName("提交报销单：金额超限应抛出异常")
    void shouldThrowExceptionWhenAmountExceedsLimit() { ... }

    @Test
    @DisplayName("撤回报销单：仅草稿和已提交状态可撤回")
    void shouldAllowWithdrawOnlyInDraftOrSubmitted() { ... }
}
```

- 测试方法命名：`should{预期行为}When{条件}` 或中文 `@DisplayName`
- 每个测试只测一个场景
- 使用 Given-When-Then 结构（安排 → 执行 → 断言）

---

## 六、Docker Compose 开发环境

```bash
# 启动所有中间件
docker compose -f docker-compose.yml up -d

# 启动所有微服务（mvn package 后）
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d

# 查看日志
docker compose logs -f gateway-service

# 全部停止
docker compose down
```

## 七、项目结构

```
ExpenseFlow/
├── pom.xml                          # 父 POM（依赖管理）
├── docker-compose.yml               # 中间件编排
├── docker-compose.services.yml      # 微服务编排
├── CLAUDE.md                        # 本文件
├── docs/
│   ├── architecture.md              # 架构设计文档
│   └── api/                         # API 文档（Knife4j 生成 + 手动补充）
├── sql/
│   └── init.sql                     # 数据库初始化（DDL + 种子数据）
├── expense-common/                  # 公共模块
├── gateway-service/                 # 网关服务
├── system-service/                  # 系统服务
├── expense-service/                 # 差旅报销服务
├── approval-service/                # 审批引擎服务
├── ai-service/                      # AI 智能服务
├── notification-service/            # 通知服务
└── expense-web/                     # Vue 3 前端
```
