# ExpenseFlow 系统架构设计文档

> 版本: 1.0 | 更新日期: 2026-05 | 负责人: 廖仙雁

---

## 目录

1. [架构概述](#1-架构概述)
2. [微服务拓扑](#2-微服务拓扑)
3. [技术架构](#3-技术架构)
4. [通信架构](#4-通信架构)
5. [多租户架构](#5-多租户架构)
6. [安全架构](#6-安全架构)
7. [高可用架构](#7-高可用架构)
8. [可观测性](#8-可观测性)

---

## 1. 架构概述

### 1.1 系统定位

ExpenseFlow 是一个面向企业差旅报销场景的智能管理平台，覆盖 **出差申请 -> 消费记录 -> 发票 OCR 识别 -> 报销提交 -> 多级审批 -> 财务打款** 全链路。系统融合 AI 能力（OCR 图像识别、DeepSeek 智能审单、RAG 知识问答、风控预警），采用微服务架构实现模块解耦与独立部署。

### 1.2 架构原则

| 原则 | 说明 |
|------|------|
| **领域驱动拆分** | 按业务边界划分微服务，每个服务拥有独立数据域，禁止跨服务直接访问数据库 |
| **同步与异步分离** | 核心业务链路使用 OpenFeign 同步调用，通知/审单等非关键路径使用 RabbitMQ 异步解耦 |
| **无状态设计** | 所有业务服务无状态，JWT 承载用户上下文，支持水平扩缩 |
| **多租户优先** | 共享数据库 + `tenant_id` 行级隔离，租户上下文通过请求头全链路透传 |
| **弹性容错** | Sentinel 限流熔断 + Feign 降级兜底，确保单点故障不扩散 |
| **可观测性** | Micrometer 指标采集 + Prometheus/Grafana 可视化 + SkyWalking 全链路追踪 |

### 1.3 系统分层

```
┌──────────────────────────────────────────────────────────────────┐
│                        接入层 (Access Layer)                       │
│             Nginx :80 (生产) / Vite :5173 (开发)                    │
├──────────────────────────────────────────────────────────────────┤
│                        网关层 (Gateway Layer)                      │
│   Spring Cloud Gateway :8080 — JWT 验签、Sentinel 限流、路由转发     │
├──────────────────────────────────────────────────────────────────┤
│                        业务层 (Business Layer)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │ system   │ │ expense  │ │ approval │ │   ai     │ │ notif  │ │
│  │ :8081    │ │ :8082    │ │ :8083    │ │ :8084    │ │ :8085  │ │
│  │ 用户/角色 │ │ 出差/报销│ │ 工作流   │ │ OCR/AI   │ │ 消息   │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └────────┘ │
├──────────────────────────────────────────────────────────────────┤
│                        数据层 (Data Layer)                         │
│        MySQL 8.0 (共享DB+多租户) | Redis 7 + Caffeine 二级缓存        │
├──────────────────────────────────────────────────────────────────┤
│                      中间件层 (Middleware Layer)                    │
│     Nacos 2.3 (注册/配置) | RabbitMQ 3.13 | Sentinel (限流)         │
├──────────────────────────────────────────────────────────────────┤
│                    可观测性层 (Observability Layer)                 │
│         Prometheus → Grafana | SkyWalking 链路追踪                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. 微服务拓扑

### 2.1 服务依赖关系图

```
                         ┌─────────────────┐
                         │   Browser / App  │
                         └────────┬────────┘
                                  │ HTTPS
                         ┌────────▼────────┐
                         │  Nginx :80      │  (生产环境)
                         │  Vite :5173     │  (开发环境)
                         └────────┬────────┘
                                  │
                ┌─────────────────▼──────────────────┐
                │       Gateway Service :8080         │
                │  · JWT 验签（白名单放行登录/刷新）      │
                │  · Redis 黑名单 Token 注销校验        │
                │  · Sentinel 限流 (100 req/s per IP) │
                │  · 路由转发 + CORS 统一配置           │
                └────┬──────┬──────┬──────┬──────┬────┘
                     │      │      │      │      │
           ┌────────▼──┐ ┌─▼──────▼──┐ ┌─▼──────▼──┐
           │  System   │ │  Expense  │ │ Approval  │
           │  :8081    │ │  :8082    │ │  :8083    │
           │           │ │           │ │           │
           │ ┌───────┐ │ │ ┌───────┐ │ │ ┌───────┐ │
           │ │租户管理│ │ │ │出差申请│ │ │ │Flowable│ │
           │ │用户/角色│ │ │ │报销单  │ │ │ │工作流  │ │
           │ │权限RBAC│ │ │ │消费记录│ │ │ │Drools  │ │
           │ │部门管理│ │ │ │发票管理│ │ │ │规则引擎│ │
           │ │OAuth2  │ │ │ │费用政策│ │ │ │审批回调│ │
           │ │SSO    │ │ │ │打款流水│ │ │ └───────┘ │
           │ └───────┘ │ │ └───────┘ │ │           │
           └──┬───┬────┘ └──┬───┬───┘ └───┬───┬───┘
              │   │         │   │         │   │
              │   └─────────┼───┼─────────┼───┘
              │             │   │         │
              │     同步调用 │   │同步调用  │同步调用
              │    (OpenFeign)  │         │
              │             │   │         │
    ┌─────────▼──┐   ┌──────▼───▼──┐  ┌──▼──────────┐
    │  AI :8084  │   │ Notification│  │   RabbitMQ   │
    │            │   │   :8085     │  │    :5672     │
    │ ┌────────┐ │   │             │  │              │
    │ │OCR 识别│ │   │ ┌─────────┐ │  │ expense.     │
    │ │DeepSeek│ │   │ │站内消息  │ │  │ exchange     │
    │ │RAG 问答│ │   │ │钉钉机器人│ │  │              │
    │ │风控预警│ │   │ │消息模板  │ │  │ (Topic)      │
    │ └────────┘ │   │ └─────────┘ │  │              │
    └────────────┘   └─────────────┘  └──────────────┘
```

**箭头说明**:
- **实线箭头**: HTTP 同步调用 (OpenFeign) 或 Nacos 服务发现
- **虚线箭头**: RabbitMQ 异步消息

### 2.2 服务端口规划

| 服务 | 容器名 | 端口 | Actuator 端点 | 依赖中间件 |
|------|--------|:---:|---------------|-----------|
| gateway-service | expense-gateway | 8080 | `/actuator/health` | Redis (黑名单) |
| system-service | expense-system | 8081 | `/actuator/health` | MySQL, Redis, Nacos |
| expense-service | expense-expense | 8082 | `/actuator/health` | MySQL, Redis, Nacos, RabbitMQ |
| approval-service | expense-approval | 8083 | `/actuator/health` | MySQL, Redis, Nacos, RabbitMQ |
| ai-service | expense-ai | 8084 | `/actuator/health` | MySQL, Nacos, RabbitMQ |
| notification-service | expense-notification | 8085 | `/actuator/health` | MySQL, Nacos, RabbitMQ |
| frontend | expense-frontend | 80 | - | Gateway (转发依赖) |

### 2.3 服务职责边界

| 服务 | 核心职责 | 不负责 |
|------|---------|--------|
| **gateway** | 统一入口、JWT 验签、限流、CORS、路由 | 业务逻辑、权限校验(仅验签名) |
| **system** | 租户/用户/角色/权限 CRUD、RBAC 授权、OAuth2 SSO | 出差/报销业务 |
| **expense** | 出差申请、报销单、发票上传与 OCR 触发、费用政策校验 | 审批流转、通知推送 |
| **approval** | Flowable 工作流编排、Drools 金额规则、审批操作 | 用户管理、报销单 CRUD |
| **ai** | OCR 发票识别、DeepSeek 审单、RAG 问答、风控分析 | 业务数据 CRUD |
| **notification** | 站内消息写入、钉钉机器人推送、消息模板渲染 | 业务决策、审批规则 |

---

## 3. 技术架构

### 3.1 技术栈全景

```
┌────────────────────────────────────────────────────────────────────┐
│                        前端 (Presentation)                          │
│  Vue 3.4 + TypeScript + Vite + Element Plus + ECharts + Pinia      │
│  路由守卫 + Axios 拦截器 (JWT Bearer Token 自动注入)                │
├────────────────────────────────────────────────────────────────────┤
│                        网关层 (Gateway)                             │
│  Spring Cloud Gateway 4.x (Reactive) + Sentinel 1.8 + Redis 7      │
├────────────────────────────────────────────────────────────────────┤
│                        业务层 (Business)                            │
│  Spring Boot 3.3 + Spring Cloud 2023 + JDK 17                      │
│  OpenFeign (远程调用) + Sentinel (熔断降级)                          │
│  Flowable 7.0 (工作流) + Drools 9 (规则引擎)                        │
│  LangChain4j 0.35 (AI 编排)                                        │
├────────────────────────────────────────────────────────────────────┤
│                        数据层 (Data)                                │
│  MyBatis-Plus 3.5 (ORM + 多租户拦截) + HikariCP (连接池)            │
│  MySQL 8.0 (主存储) + Redis 7 (缓存+黑名单) + Caffeine (本地缓存)    │
├────────────────────────────────────────────────────────────────────┤
│                        中间件层 (Middleware)                        │
│  Nacos 2.3 (注册发现 + 配置中心) + RabbitMQ 3.13 (消息队列)          │
├────────────────────────────────────────────────────────────────────┤
│                        部署层 (Deployment)                          │
│  Docker Compose (开发/演示) + Nginx (前端静态资源)                   │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 各层技术选型理由

#### 3.2.1 接入层 — Nginx + Vite

- **生产环境**: Nginx 作为反向代理，将前端静态资源请求与 API 请求统一转发至 Gateway
- **开发环境**: Vite Dev Server (`:5173`) 配置代理将 `/system/**`、`/expense/**` 等 API 路径转发至 Gateway `:8080`
- **选型理由**: Nginx 成熟稳定、配置简单；Vite 原生支持代理，无需额外安装 CORS 插件

#### 3.2.2 网关层 — Spring Cloud Gateway

- **选型理由**:
  - 基于 Spring WebFlux (Reactor) 的响应式网关，非阻塞 I/O，适合高并发场景
  - 与 Spring Cloud 生态深度集成，无缝对接 Nacos 服务发现 (通过 `lb://service-name` 负载均衡)
  - 内置 Sentinel 网关限流支持 (`spring.cloud.sentinel.transport.port=8719`)
  - Redis Reactive 驱动 Token 黑名单校验 (非阻塞)

- **路由配置** (实际配置来自 `gateway-service/application.yml`):

```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true          # 自动根据 Nacos 服务名创建路由
          lower-case-service-id: true
      routes:
        - id: system-service
          uri: lb://system-service    # 负载均衡到 system-service 实例
          predicates:
            - Path=/system/**
        - id: expense-service
          uri: lb://expense-service
          predicates:
            - Path=/expense/**
        - id: approval-service
          uri: lb://approval-service
          predicates:
            - Path=/approval/**
        - id: ai-service
          uri: lb://ai-service
          predicates:
            - Path=/ai/**
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/notification/**
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Origin
```

#### 3.2.3 注册与配置中心 — Nacos 2.3

- **选型理由**:
  - 同时支持服务注册发现和动态配置管理，减少中间件数量
  - 与 Spring Cloud Alibaba 生态无缝集成 (`spring.cloud.nacos.discovery` + `spring.cloud.nacos.config`)
  - 支持配置热刷新，适合频繁调整的规则/阈值
  - 生产环境使用 standalone 模式运行，健康检查端点: `http://localhost:8848/nacos/v1/console/health/liveness`

- **配置分层策略**:
  - `application.yml`: 各服务本地默认配置 (端口、数据源、MyBatis-Plus)
  - `nacos:{service-name}.yaml`: Nacos 远程动态配置 (日志级别、业务开关、阈值参数)
  - 通过 `spring.config.import: "optional:nacos:${spring.application.name}.yaml"` 按需导入

#### 3.2.4 业务层 — Spring Boot 3.3 + MyBatis-Plus 3.5

- **选型理由**:
  - Spring Boot 3.3 基于 JDK 17，支持虚拟线程 (未启用)、原生编译
  - MyBatis-Plus 提供开箱即用的多租户插件 (`TenantLineHandler`)、逻辑删除、分页
  - HikariCP 连接池，每个服务最大连接数 20，共享数据库 `expense_flow`

- **工作流引擎 — Flowable 7**:
  - BPMN 2.0 标准工作流引擎，支持出差申请/报销单审批流程定义
  - 自动创建 60+ 引擎表 (前缀 `act_`/`flw_`)，通过 `flowable.database-schema-update: true` 自动同步表结构
  - 审批操作通过 Flowable TaskService 完成任务，回调通过 Feign 回写业务状态

- **规则引擎 — Drools 9**:
  - 将动态规则 (如"金额 > 5000 触发总监审批") 从 Java 代码抽离到 DRL 规则文件
  - 内嵌 Java fallback，未配置 DRL 时自动降级为等价 Java 逻辑
  - 核心代码示例:

```java
// approval-service/.../DroolsRuleService.java
public RuleOutput evaluate(String businessType, BigDecimal amount) {
    RuleInput input = new RuleInput(businessType, amount.doubleValue());
    RuleOutput output = new RuleOutput();

    if (kieContainer != null) {
        // 使用 Drools 规则引擎
        KieSession session = kieContainer.newKieSession();
        session.insert(input);
        session.insert(output);
        session.fireAllRules();
        session.dispose();
    } else {
        // Java fallback: 规则等价于 DRL
        if ("TRAVEL_REQUEST".equals(businessType) && input.getAmount() > 5000) {
            output.setNeedDirector(true);
        }
        if ("EXPENSE_REPORT".equals(businessType) && input.getAmount() > 20000) {
            output.getWarnings().add("报销金额超过20000，建议总监复核");
        }
    }
    return output;
}
```

- **AI 编排 — LangChain4j 0.35**:
  - 统一的 LLM 调用抽象层，当前集成 DeepSeek Chat API
  - 配置基于 OpenAI 兼容协议，通过 `langchain4j.open-ai.chat-model.base-url=https://api.deepseek.com` 接入
  - 支持超时重试 (30s timeout, max 2 retries)

#### 3.2.5 数据层

**MySQL 8.0**:
- 共享数据库 `expense_flow`，字符集 `utf8mb4_unicode_ci`
- 核心业务表 25 张 (sys_* 12 + ex_* 7 + ap_* 1 + ai_* 3 + nt_* 2) + Flowable 引擎表 60+
- 全部表使用逻辑删除 (`deleted TINYINT DEFAULT 0`)，MyBatis-Plus 自动注入条件
- 唯一索引包含 `tenant_id` 确保租户间数据隔离

**Redis 7 + Caffeine 二级缓存**:
- **L1 — Caffeine 本地缓存**: 用户信息 (10min)、用户权限 (10min)、部门树 (5min)，最大条目 100-1000
- **L2 — Redis 分布式缓存**: Token 黑名单、幂等 Token、业务数据缓存
- 读流程: Caffeine -> Redis -> MySQL；写流程: 删除 Caffeine + Redis + 更新 MySQL

```java
// system-service/.../CacheConfig.java
@Bean
public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    CaffeineCache userCache = new CaffeineCache("user",
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
    CaffeineCache userPermCache = new CaffeineCache("userPerm",
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000).build());
    CaffeineCache deptTreeCache = new CaffeineCache("deptTree",
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(100).build());
    manager.setCaches(Arrays.asList(userCache, userPermCache, deptTreeCache));
    return manager;
}
```

#### 3.2.6 中间件层

- **Nacos 2.3**: 注册发现 + 配置中心 (见 3.2.3)
- **RabbitMQ 3.13**: 异步消息队列，管理界面 `:15672` (见第 4 章)
- **Sentinel**: 流量控制 + 熔断降级 (见第 7 章)

#### 3.2.7 可观测性层

- **Prometheus**: 从各服务 `/actuator/prometheus` 采集 Micrometer 指标，15s 间隔
- **Grafana**: 可视化面板，默认账号 admin/admin
- **SkyWalking 10**: Java Agent 自动埋点，全链路追踪 (规划中)

---

## 4. 通信架构

### 4.1 同步调用 (OpenFeign)

用于核心业务链路中需要即时响应的场景 (如提交审批、查询用户信息)。Feign 接口定义在**调用方服务**的 `feign/` 包下。

#### 4.1.1 Feign 客户端清单

| 调用方 | 被调用方 | 用途 | Feign 接口路径 |
|--------|---------|------|---------------|
| expense-service | approval-service | 提交报销单后启动审批流 | `expense/feign/ApprovalFeignClient.java` |
| expense-service | system-service | 获取用户信息、部门信息 | `expense/feign/SystemFeignClient.java` |
| approval-service | expense-service | 审批完成后回写业务状态 | `approval/feign/ExpenseFeignClient.java` |
| approval-service | system-service | 获取审批人信息 | `approval/feign/SystemFeignClient.java` |
| ai-service | expense-service | 获取报销单详情用于审单 | `ai/feign/ExpenseFeignClient.java` |
| ai-service | system-service | 获取用户/租户上下文 | `ai/feign/SystemFeignClient.java` |
| notification-service | system-service | 获取用户信息用于通知 | `notification/feign/SystemFeignClient.java` |

#### 4.1.2 Feign 调用规范

```java
// expense-service/.../feign/ApprovalFeignClient.java — 启动审批流
@FeignClient(name = "approval-service", path = "/approval",
             fallbackFactory = ApprovalFeignFallbackFactory.class)
public interface ApprovalFeignClient {
    @PostMapping("/process/start")
    Result<ApprovalProcessStartResponse> startApproval(@RequestBody ApprovalStartDTO dto);
}

// expense-service/.../feign/SystemFeignClient.java — 查询用户
@FeignClient(name = "system-service", path = "/system",
             fallbackFactory = SystemFeignFallbackFactory.class)
public interface SystemFeignClient {
    @GetMapping("/user/{id}")
    Result<SystemUserDTO> getUser(@PathVariable Long id);

    @GetMapping("/department/{id}")
    Result<SystemDeptDTO> getDepartment(@PathVariable Long id);
}
```

#### 4.1.3 Sentinel 降级策略

每个 Feign 客户端强制配置 `fallbackFactory`，当被调用方不可用时返回降级响应:

```java
// expense-service/.../feign/fallback/ApprovalFeignFallbackFactory.java
@Slf4j
@Component
public class ApprovalFeignFallbackFactory implements FallbackFactory<ApprovalFeignClient> {

    @Override
    public ApprovalFeignClient create(Throwable cause) {
        log.error("approval-service 调用失败, 使用降级: {}", cause.getMessage());
        return new ApprovalFeignClient() {
            @Override
            public Result<ApprovalProcessStartResponse> startApproval(ApprovalStartDTO dto) {
                // 返回带降级标记的响应，业务方可据此判断是否降级
                ApprovalProcessStartResponse resp = new ApprovalProcessStartResponse();
                resp.setProcessInstanceId("fallback-pi-" + UUID.randomUUID().toString().substring(0, 12));
                resp.setApprovalLevel("SINGLE");
                resp.setWarnings(Collections.emptyList());
                return Result.ok(resp);
            }
        };
    }
}
```

### 4.2 异步消息 (RabbitMQ)

用于非关键路径的解耦场景 (AI 审单、通知推送)。消息体统一使用 JSON 格式，消费端基于 `eventId` (UUID) 做幂等去重。

#### 4.2.1 Exchange / Queue 拓扑

```
                         expense.exchange (Topic)
                               │
              ┌────────────────┼────────────────────┐
              │                │                    │
    expense.report.    expense.result.      ai.review.
    submitted          notified             completed
              │                │                    │
     ┌────────▼──────┐  ┌──────▼───────┐  ┌────────▼──────┐
     │ai.review.queue│  │notification. │  │notification.  │
     │               │  │event.queue   │  │event.queue    │
     │ (durable)     │  │(durable)     │  │(durable)      │
     └──────┬────────┘  └──────┬───────┘  └──────┬────────┘
            │                  │                  │
      ┌─────▼─────┐     ┌──────▼───────┐   ┌──────▼───────┐
      │ai-service │     │notification-  │   │notification-  │
      │审单消费者  │     │service        │   │service        │
      │           │     │通知消费者      │   │通知消费者      │
      └───────────┘     └──────────────┘   └──────────────┘
```

#### 4.2.2 消息事件表

| Exchange | Routing Key | Queue | 生产者 | 消费者 | 触发时机 |
|----------|-------------|-------|--------|--------|---------|
| `expense.exchange` | `expense.report.submitted` | `ai.review.queue` | expense-service | ai-service | 报销单提交后 |
| `expense.exchange` | `expense.result.notified` | `notification.event.queue` | expense-service | notification-service | 报销单状态变更 |
| `expense.exchange` | `ai.review.completed` | `notification.event.queue` | ai-service | notification-service | AI 审单完成 |

#### 4.2.3 RabbitMQ 配置代码

```java
// ai-service/.../config/RabbitMQConfig.java — 审单队列声明
@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "expense.exchange";
    public static final String REVIEW_QUEUE = "ai.review.queue";
    public static final String REVIEW_KEY = "expense.report.submitted";

    @Bean
    public TopicExchange expenseExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE).build();    // 持久化队列
    }

    @Bean
    public Binding reviewBinding() {
        return BindingBuilder.bind(reviewQueue()).to(expenseExchange()).with(REVIEW_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();             // JSON 序列化
    }
}
```

```java
// notification-service/.../config/RabbitMQConfig.java — 通知队列声明
@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "expense.exchange";
    public static final String NOTIFY_QUEUE = "notification.event.queue";
    public static final String RESULT_KEY = "expense.result.notified";
    public static final String REVIEW_KEY = "ai.review.completed";

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(RESULT_KEY);
    }

    @Bean
    public Binding reviewCompletedBinding() {
        return BindingBuilder.bind(notifyQueue()).to(expenseExchange()).with(REVIEW_KEY);
    }
}
```

#### 4.2.4 消息消费者

```java
// ai-service/.../service/RabbitMQConsumer.java — AI 审单消费者
@RabbitListener(queues = "ai.review.queue")
public void onReportSubmitted(Map<String, Object> map) {
    log.info("收到 AI 审单消息: {}", map);
    ReviewRequestDTO dto = new ReviewRequestDTO();
    dto.setBusinessType("EXPENSE_REPORT");
    dto.setBusinessId(Long.valueOf(map.get("reportId").toString()));
    dto.setTotalAmount(new BigDecimal(map.get("amount").toString()));
    reviewService.review(dto, getTenantId(map));
}

// notification-service/.../service/RabbitMQConsumer.java — 通知消费者
@RabbitListener(queues = "notification.event.queue")
public void onNotificationEvent(Map<String, Object> map) {
    String eventType = (String) map.getOrDefault("eventType", "unknown");
    // 根据事件类型构造通知消息
    if ("APPROVED".equals(map.get("outcome")) || "REJECTED".equals(map.get("outcome"))) {
        String resultText = "APPROVED".equals(map.get("outcome")) ? "通过" : "驳回";
        title = "审批结果通知";
        content = "您的" + businessType + "已" + resultText;
    }
    // 双写：站内消息 + 钉钉推送
    messageService.send(userId, title, content, "NOTIFICATION", businessType, businessId, tenantId);
    dingTalkService.send(title, content);
}
```

### 4.3 典型调用链路

#### 场景: 用户提交报销单

```
1. Browser → Gateway POST /expense/report/submit
2. Gateway:
   ├── JWT 验签 (Bearer Token 解析 → 用户ID/租户ID)
   ├── Redis 检查 Token 黑名单
   └── 路由转发至 expense-service
3. expense-service:
   ├── 写入 ex_expense_report (状态=DRAFT → SUBMITTED)
   ├── [同步] Feign → approval-service: 启动审批流
   │   approval-service:
   │   ├── Flowable 创建流程实例
   │   ├── Drools 评估审批等级 (金额>5000? 总监审批)
   │   └── 返回审批结果
   ├── [异步] RabbitMQ → ai.review.queue: 报销单已提交
   │   ai-service:
   │   └── DeepSeek API 调用 → 写入 ai_review_result
   └── 返回 Result<ExpenseReportVO> 给前端
4. 审批完成后:
   approval-service:
   ├── [同步] Feign → expense-service: 回写审批结果
   └── [异步] RabbitMQ → notification.event.queue: 审批完成
       notification-service:
       ├── 写入 nt_message (站内消息)
       └── 调用钉钉 Webhook 推送通知
```

---

## 5. 多租户架构

### 5.1 数据隔离方案

采用 **共享数据库 + tenant_id 行级隔离** 方案:

- 所有租户共用同一个 MySQL 数据库 `expense_flow`
- 每张业务表包含 `tenant_id BIGINT NOT NULL` 字段
- MyBatis-Plus 多租户插件在 SQL 执行时自动注入 `WHERE tenant_id = ?`
- 唯一索引包含 `tenant_id` 确保租户间数据不会冲突

**隔离白名单** (以下表不注入 tenant_id，因为它们是全局配置):

```java
// expense-common/.../handler/ExpenseFlowTenantLineHandler.java
public class ExpenseFlowTenantLineHandler implements TenantLineHandler {
    @Override
    public boolean ignoreTable(String tableName) {
        // 租户表本身就是管理多租户的，不需要过滤
        // 权限表是系统全局预置的 RBAC 模型
        return "sys_tenant".equalsIgnoreCase(tableName)
                || "sys_permission".equalsIgnoreCase(tableName)
                || "sys_user_role".equalsIgnoreCase(tableName)
                || "sys_role_permission".equalsIgnoreCase(tableName);
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = CURRENT_TENANT.get();
        return tenantId != null ? new LongValue(tenantId) : new LongValue(0);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    // ThreadLocal 存储当前请求的租户上下文
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    public static void setTenant(Long tenantId) { CURRENT_TENANT.set(tenantId); }
    public static Long getTenant() { return CURRENT_TENANT.get(); }
    public static void clear() { CURRENT_TENANT.remove(); }
}
```

### 5.2 租户上下文全链路透传

```
┌─────────────────────────────────────────────────────────────────────┐
│  前端                                                               │
│  localStorage 存储 userInfo.tenantId                                │
│  Axios 请求拦截器注入: headers['X-Tenant-Id'] = tenantId             │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ GET /expense/report/list
                       │ Header: Authorization: Bearer <token>
                       │ Header: X-Tenant-Id: 1001
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Gateway (JwtAuthGatewayFilter)                                     │
│  1. 白名单放行 (登录/刷新/健康检查)                                   │
│  2. JWT 解析获取 userId、tenantId                                    │
│  3. Redis 黑名单校验 tokenId                                         │
│  4. 添加透传头: X-User-Id, X-Tenant-Id, X-Username                  │
│     → 转发至下游                                                     │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ X-Tenant-Id: 1001 (透传)
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│  expense-service                                                    │
│  TenantContextFilter (Order=1):                                      │
│    req.getHeader("X-Tenant-Id") → ThreadLocal                       │
│    → ExpenseFlowTenantLineHandler.setTenant(1001)                   │
│                                                                     │
│  MyBatis-Plus 拦截器自动注入:                                        │
│    SELECT * FROM ex_expense_report                                  │
│    WHERE tenant_id = 1001 AND deleted = 0                           │
│                                                                     │
│  OpenFeign 调用时通过 RequestInterceptor 自动透传 X-Tenant-Id        │
└──────────────────────┬──────────────────────────────────────────────┘
                       │ Feign 调用: approval-service
                       │ Header: X-Tenant-Id: 1001
                       ▼
┌─────────────────────────────────────────────────────────────────────┐
│  approval-service                                                   │
│  同样通过 TenantContextFilter 提取 → ThreadLocal → SQL 注入           │
└─────────────────────────────────────────────────────────────────────┘
```

**关键点**:
- Gateway 是租户上下文的权威来源 (从 JWT 解析)
- 下游服务信任 Gateway 注入的请求头 (内网信任域)
- `TenantContextFilter` 使用 `@Order(1)` 确保在所有 Filter 中最先执行
- 请求结束时 `finally` 块清除 `ThreadLocal` 防止内存泄漏

---

## 6. 安全架构

### 6.1 JWT 认证流程

```
┌────────┐      ┌─────────┐      ┌──────────┐      ┌─────────┐
│ Browser│      │ Gateway │      │  System  │      │  Redis  │
└───┬────┘      └────┬────┘      └────┬─────┘      └────┬────┘
    │                │                │                  │
    │ 1. POST /system/auth/login      │                  │
    │  {username, password, tenantId} │                  │
    │───────────────>│───────────────>│                  │
    │                │                │                  │
    │                │    2. BCrypt 密码校验              │
    │                │    3. 生成 Access Token (2h)      │
    │                │       Refresh Token (7d)          │
    │                │                │                  │
    │ 4. {accessToken, refreshToken}  │                  │
    │<───────────────│<───────────────│                  │
    │                │                │                  │
    │ 5. 存储 localStorage             │                  │
    │                │                │                  │
    │ 6. GET /expense/report/list     │                  │
    │  Header: Authorization:         │                  │
    │    Bearer <accessToken>         │                  │
    │───────────────>│                │                  │
    │                │                │                  │
    │                │ 7. JWT 验签     │                  │
    │                │ 8. 提取 userId=1,tenantId=1001    │
    │                │                │                  │
    │                │ 9. 黑名单校验?   │                  │
    │                │───────────────>│ token:blacklist: │
    │                │                │    <tokenId>     │
    │                │<───────────────│   (不存在=通过)   │
    │                │                │                  │
    │                │ 10. 透传 X-User-Id, X-Tenant-Id  │
    │                │───────────────>│  expense-service │
    │                │                │                  │
    │                │ 11. 登出 POST /system/auth/logout │
    │<───────────────│<───────────────│                  │
    │                │   12. Redis SET token:blacklist:<tokenId> = 1
    │                │       EXPIRE = Access Token 剩余有效期 │
    │                │─────────────────────────────────>│
```

**Token 结构** (JWT Payload):
```json
{
  "jti": "uuid-token-id",
  "sub": "1",
  "tenantId": 1001,
  "iat": 1706000000,
  "exp": 1706007200
}
```

**JWT 工具类核心代码** (`expense-common/.../util/JwtUtil.java`):
```java
public class JwtUtil {
    private static final String SECRET = "ExpenseFlow2026SecretKeyForJWTTokenGenerationMustBeLongEnough!!";
    private static final long ACCESS_EXPIRE = 2 * 60 * 60 * 1000L;     // 2h
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7d

    public static String generateAccessToken(Long userId, Long tenantId, String tokenId) {
        return Jwts.builder()
                .id(tokenId)
                .subject(String.valueOf(userId))
                .claim("tenantId", tenantId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRE))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
```

**Gateway JWT 验签过滤器** (`gateway-service/.../filter/JwtAuthGatewayFilter.java`):
- 实现 `GlobalFilter` + `Ordered(-100)` 确保最先执行
- 白名单路径: `/system/auth/login`, `/system/auth/refresh`, `/system/oauth/**`, `/actuator/**`
- Redis Reactive 驱动黑名单校验: `token:blacklist:{tokenId}` key 存在则拒绝
- 解析后注入请求头: `X-User-Id`, `X-Tenant-Id`, `X-Username`

### 6.2 RBAC 授权模型

```
┌──────────┐     ┌───────────────┐     ┌──────────────┐
│ sys_user │────>│ sys_user_role │<────│  sys_role    │
│ (用户)    │ N:M │ (用户角色关联) │ N:M │  (角色)       │
└──────────┘     └───────────────┘     └──────┬───────┘
                                              │
                                     ┌────────▼────────┐
                                     │ sys_role_perm   │
                                     │ (角色权限关联)    │
                                     └────────┬────────┘
                                              │
                                     ┌────────▼────────┐
                                     │ sys_permission  │
                                     │ · menu (菜单)    │
                                     │ · button (按钮)  │
                                     │ · API (接口)     │
                                     └─────────────────┘
```

**权限标识示例**:
- `expense:report:create` — 创建报销单
- `expense:report:delete` — 删除报销单
- `approval:process:approve` — 审批操作
- `system:user:list` — 查看用户列表

**方法级授权** (通过 `@PreAuthorize` 注解):
```java
@PreAuthorize("@pms.hasPermission('expense:report:delete')")
@DeleteMapping("/report/{id}")
public Result<Void> deleteReport(@PathVariable Long id) { ... }
```

### 6.3 数据脱敏

在 VO 层标注 `@Sensitive` 注解，Jackson 序列化时自动脱敏:
- **手机号**: `138****1234` (保留前3后4)
- **银行卡号**: `****1234` (仅保留后4位)
- **身份证号**: `110101****1234****` (保留前6后4)

### 6.4 接口幂等

创建/提交类接口使用 **Token 机制** 防重复提交:
1. 前端调用 `GET /expense/idempotent-token` 获取 UUID Token
2. 后端将 Token 存入 Redis: `SET idempotent:{token} = 1 EX 300` (5分钟过期)
3. 前端提交时携带 Header `Idempotent-Token: {token}`
4. 后端消费 Token: Redis `DEL idempotent:{token}` 成功 → 放行；失败 → 拒绝重复提交

### 6.5 其他安全措施

| 措施 | 实现 |
|------|------|
| **密码加密** | BCrypt (Spring Security 默认强度 10) |
| **敏感配置加密** | 数据库密码、API Key 通过 Docker Compose 环境变量注入 |
| **SQL 注入防护** | MyBatis-Plus 参数化查询 `#{}`，禁止 `${}` |
| **文件上传限制** | 仅 PNG/JPG/PDF，大小限制 10MB (Spring `multipart.max-file-size`) |
| **CORS 配置** | Gateway 全局 CORS，开发阶段 `allowedOriginPatterns: "*"` (生产待收紧) |
| **操作审计** | `sys_audit_log` 表记录所有 INSERT/UPDATE/DELETE 操作 (含 IP/UA/耗时/变更前后值) |

---

## 7. 高可用架构

### 7.1 Sentinel 限流与熔断

#### 7.1.1 网关层限流

Gateway 集成 Sentinel 网关流控，在入口处拦截过载流量:

```java
// gateway-service/.../config/GatewayConfig.java
@Configuration
public class GatewayConfig {
    @PostConstruct
    public void initSentinelBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) ->
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"));
    }
}
```

**限流策略**:
- 每个用户/IP 100 req/s (Gateway Sentinel 规则)
- 核心接口 (提交报销单、启动审批) 额外 10 req/s 限制
- 被限流时返回 HTTP 429 + JSON 统一响应格式

#### 7.1.2 服务间熔断降级

所有 Feign 调用强制配置 `fallbackFactory`，确保被调用方故障时不扩散:

```
expense-service 调用 approval-service ：
  ┌─ 正常: approval-service 返回审批结果
  │
  └─ 异常 (超时/500/网络不通):
       └─ ApprovalFeignFallbackFactory 返回降级响应
          { processInstanceId: "fallback-pi-xxx", approvalLevel: "SINGLE", warnings: [] }
          业务方记录降级日志，后续可通过后台任务补偿
```

**Sentinel 规则建议** (Nacos 动态配置):
```json
{
  "resource": "approval-service#POST:/approval/process/start",
  "grade": 0,           // 0=线程数, 1=QPS
  "count": 10,          // 阈值
  "timeWindow": 10,     // 熔断窗口 10s
  "minRequestAmount": 5,
  "statIntervalMs": 1000
}
```

### 7.2 Redis 缓存策略

| 缓存类型 | Key 模式 | 过期策略 | 用途 |
|---------|---------|---------|------|
| Token 黑名单 | `token:blacklist:{tokenId}` | 对齐 Access Token 剩余 TTL | 登出后 Token 作废 |
| 幂等 Token | `idempotent:{uuid}` | 5 分钟 | 防重复提交 |
| 用户信息 | Caffeine L1 (10min) + Redis L2 | LRU | 减少 system-service 查询 |
| 用户权限 | Caffeine L1 (10min) | 固定过期 | 权限校验加速 |
| 部门树 | Caffeine L1 (5min) | 固定过期 | 减少树形查询递归 |

**缓存更新策略**:
- **读**: Caffeine → Redis → MySQL (Cache-Aside)
- **写**: 删除 Caffeine + 删除 Redis + 更新 MySQL (先删缓存后写库)

### 7.3 Docker 健康检查

所有容器配置健康检查，确保 `depends_on` 条件正确工作:

```yaml
# docker-compose.yml (中间件)
mysql:
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
    interval: 10s
    timeout: 5s
    retries: 5

redis:
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 3s
    retries: 5

rabbitmq:
  healthcheck:
    test: ["CMD", "rabbitmqctl", "status"]
    interval: 10s
    timeout: 5s
    retries: 5

nacos:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8848/nacos/v1/console/health/liveness"]
    interval: 15s
    timeout: 5s
    retries: 10
```

**服务启动顺序** (由 `depends_on` + `condition: service_healthy` 保证):

```
MySQL → Nacos → Redis → RabbitMQ (中间件层，并行启动)
  │
  ▼
system-service (依赖 MySQL + Nacos + Redis)
expense-service (依赖 MySQL + Nacos + Redis + RabbitMQ)
approval-service (依赖 MySQL + Nacos + Redis + RabbitMQ)
ai-service (依赖 MySQL + Nacos + RabbitMQ)
notification-service (依赖 MySQL + Nacos + RabbitMQ)
  │
  ▼
gateway-service (依赖 Nacos + Redis)
  │
  ▼
frontend (依赖所有后端服务)
```

### 7.4 容灾设计

| 故障场景 | 影响范围 | 降级策略 |
|---------|---------|---------|
| Nacos 宕机 | 新服务无法注册/发现 | 已建立的连接不受影响 (本地缓存)；重启需 Nacos 先恢复 |
| Redis 宕机 | Token 黑名单不可用，Caffeine 缓存正常工作 | 已签发 Token 仍可通过签名校验，注销功能暂时失效 |
| RabbitMQ 宕机 | AI 审单、通知推送延迟 | 消息持久化到磁盘，MQ 恢复后自动投递 |
| MySQL 宕机 | 所有服务不可写，Gateway 验签可读 | 快速恢复；考虑主从复制 (生产) |
| AI Service 宕机 | OCR/DeepSeek 不可用 | expense-service 记录未处理任务，待恢复后补偿 |
| Approval Service 宕机 | 无法提交新审批 | Feign 降级返回临时审批号，恢复后补偿启动 |

---

## 8. 可观测性

### 8.1 指标采集 (Micrometer → Prometheus)

所有微服务暴露 `/actuator/prometheus` 端点，Prometheus 每 15 秒采集一次:

```yaml
# 各服务 application.yml 统一配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  prometheus:
    metrics:
      export:
        enabled: true
```

```yaml
# config/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'expenseflow-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'gateway-service:8080'
          - 'system-service:8081'
          - 'expense-service:8082'
          - 'approval-service:8083'
          - 'ai-service:8084'
          - 'notification-service:8085'
```

**关键指标**:
- **JVM**: `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, `jvm_threads_live`
- **HTTP**: `http_server_requests_seconds_count`, `http_server_requests_seconds_sum` (请求量/耗时)
- **数据源**: `hikaricp_connections_active`, `hikaricp_connections_pending` (连接池)
- **Feign**: `feign_client_requests_seconds` (远程调用耗时)
- **RabbitMQ**: `spring_rabbitmq_listener_seconds` (消息处理耗时)

### 8.2 可视化面板 (Grafana)

- **访问**: `http://localhost:3000` (默认 admin/admin)
- **面板推荐**:
  - 服务健康状态总览 (UP/DOWN 指示灯)
  - QPS 趋势图 (各服务对比)
  - P95/P99 响应延迟 (识别慢接口)
  - Feign 调用成功率 (服务间调用健康度)
  - JVM 堆内存使用率 (GC 频繁预警)

### 8.3 链路追踪 (SkyWalking 10 — 规划中)

SkyWalking Java Agent 通过 `-javaagent` 自动埋点，追踪全链路:

```
Browser → Nginx → Gateway → expense-service → approval-service
                                  │
                                  ├── Feign → system-service
                                  └── RabbitMQ → ai-service → notification-service
```

每个 Span 记录:
- 服务名 + 实例 ID
- 操作路径 (HTTP method + URI)
- 耗时 + 状态码
- 跨服务 Trace ID 关联

通过 SkyWalking UI 可以:
- 查看完整调用链拓扑图
- 定位慢调用瓶颈
- 发现异常请求并回放

### 8.4 日志规范

| 级别 | 使用场景 |
|------|---------|
| ERROR | 需要人工介入的异常 (数据库宕机、API 调用失败) |
| WARN | 可自动恢复的异常 (JWT 解析失败、缓存未命中) |
| INFO | 关键业务节点 (提交报销单、审批通过、通知发送) |
| DEBUG | 诊断信息 (Feign 调用耗时、SQL 参数、租户上下文) |

- 日志格式: `[服务名] [TraceId] [线程] 级别 类名 - 消息`
- 敏感信息 (密码、Token) 禁止写入日志
- 生产环境 INFO 级别，开发环境 DEBUG 级别

---

## 附录

### A. 数据库表分类总览

| 前缀 | 数量 | 所属服务 | 说明 |
|------|:---:|---------|------|
| `sys_` | 12 | system-service | 租户/用户/角色/权限/部门/员工/字典/审计/OAuth |
| `ex_` | 7 | expense-service | 出差申请/报销单/明细/发票/消费记录/打款/费用政策 |
| `ap_` | 1 | approval-service | 审批记录 (业务维度双写，与 Flowable 引擎表互补) |
| `ai_` | 3 | ai-service | OCR 识别结果 / AI 审单结果 / 置信度统计 |
| `nt_` | 2 | notification-service | 站内消息 / 消息模板 |
| `act_`/`flw_` | 60+ | Flowable 引擎 | 流程定义/实例/任务/变量/历史 (框架自动创建) |

### B. 环境变量清单

| 变量名 | 用途 | 默认值 | 涉及服务 |
|--------|------|--------|---------|
| `DB_PASSWORD` | MySQL 密码 | `root` | system/expense/approval/ai/notification |
| `OCR_APP_CODE` | 阿里云 OCR AppCode | (空=Mock) | expense-service |
| `OCR_APP_KEY` | 阿里云 OCR AppKey | (空=Mock) | expense-service |
| `OCR_APP_SECRET` | 阿里云 OCR AppSecret | (空=Mock) | expense-service |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | `sk-default` | ai-service |
| `DINGTALK_WEBHOOK_URL` | 钉钉机器人 Webhook | (空=Mock) | notification-service |
| `DINGTALK_SECRET` | 钉钉机器人签名密钥 | (空=Mock) | notification-service |

### C. 端口占用一览

| 端口 | 服务 | 协议 | 说明 |
|:---:|------|------|------|
| 80 | Nginx / Frontend | HTTP | 前端静态资源 + API 反向代理 |
| 3000 | Grafana | HTTP | 监控可视化 |
| 3307 | MySQL | TCP | 映射宿主机 3307 -> 容器 3306 |
| 5672 | RabbitMQ | AMQP | 消息队列 |
| 6379 | Redis | TCP | 缓存 |
| 8080 | Gateway | HTTP | API 统一入口 |
| 8081 | System Service | HTTP | 系统服务 |
| 8082 | Expense Service | HTTP | 差旅报销服务 |
| 8083 | Approval Service | HTTP | 审批引擎服务 |
| 8084 | AI Service | HTTP | AI 智能服务 |
| 8085 | Notification Service | HTTP | 通知服务 |
| 8719 | Sentinel Dashboard | TCP | Sentinel 控制台 (Gateway 内置) |
| 8848 | Nacos | HTTP | 注册中心 + 配置中心 |
| 9090 | Prometheus | HTTP | 指标采集 |
| 15672 | RabbitMQ Management | HTTP | RabbitMQ 管理界面 |
