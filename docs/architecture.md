# ExpenseFlow 系统架构文档

## 1. 系统概述

基于 Spring Boot 3.3 + Spring Cloud 2023 + Vue 3 的微服务差旅报销智能管理平台，覆盖出差申请→消费记录→报销提交→多级审批→财务打款全链路，融合 OCR / DeepSeek 审单 / RAG 问答 / 风控预警 AI 能力。

## 2. 微服务拓扑

```
                    ┌──────────────┐
                    │   Browser    │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  Nginx :80   │ (生产) / Vite :5173 (开发)
                    └──────┬───────┘
                           │
              ┌────────────▼────────────┐
              │  Gateway Service :8080   │  JWT 验签 / Sentinel 限流 / 路由转发
              └────────────┬────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼────┐      ┌──────▼──────┐    ┌─────▼─────┐
   │ System  │      │  Expense    │    │ Approval  │
   │ :8081   │◄────►│  :8082      │◄──►│ :8083     │
   │ 用户/角色│      │ 出差/报销/发票│    │ Flowable  │
   └─────────┘      └──────┬──────┘    │ Drools    │
                           │           └───────────┘
                    ┌──────▼──────┐
                    │     AI      │
                    │   :8084     │
                    │ OCR/DeepSeek│
                    │  RAG/风控   │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Notification│
                    │   :8085     │
                    │ 消息/钉钉    │
                    └─────────────┘
```

### 服务清单

| 服务 | 端口 | 职责 | 技术 |
|------|:---:|------|------|
| gateway-service | 8080 | 网关、JWT 验签、Sentinel 限流 | Spring Cloud Gateway |
| system-service | 8081 | 租户/用户/角色/权限(RBAC)、部门 | Spring Security + JWT |
| expense-service | 8082 | 出差申请、报销单、发票、消费记录、打款 | MyBatis-Plus |
| approval-service | 8083 | 工作流引擎、规则引擎、审批操作 | Flowable 7 + Drools 9 |
| ai-service | 8084 | OCR 识别、DeepSeek 审单、RAG 问答 | LangChain4j 0.35 |
| notification-service | 8085 | 站内消息、钉钉机器人 | RabbitMQ 3.13 |

## 3. 技术栈

| 层 | 技术 |
|---|------|
| 后端框架 | Spring Boot 3.3 + Spring Cloud 2023 + JDK 17 |
| 微服务治理 | Nacos 2.3 (注册/配置) + OpenFeign + Sentinel |
| 工作流/规则 | Flowable 7 + Drools 9 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 (共享数据库 + tenant_id 多租户) |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| AI | LangChain4j 0.35 + DeepSeek Chat API |
| 可观测性 | Micrometer → Prometheus → Grafana |
| 前端 | Vue 3.4 + TypeScript + Vite + Element Plus + ECharts |
| 部署 | Docker Compose |

## 4. 数据库设计

共享数据库 `expense_flow`，通过 `tenant_id` 实现多租户隔离。

### 表分类

| 前缀 | 数量 | 说明 |
|------|:---:|------|
| sys_ | 12 | 系统服务 (租户/用户/角色/权限/部门/员工/字典/审计/OAuth) |
| ex_ | 7 | 差旅报销 (出差申请/报销单/明细/发票/消费记录/打款/费用政策) |
| ap_ | 1 | 审批记录 (业务维度双写) |
| ai_ | 3 | AI 结果 (OCR/审单/置信度统计) |
| nt_ | 2 | 通知 (站内消息/模板) |
| act_/flw_ | 60+ | Flowable 引擎表 (框架自动创建) |

## 5. API 规范

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1706000000000,
  "requestId": "uuid"
}
```

### 分页响应

```json
{
  "code": 200,
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

## 6. 安全设计

- **认证**: JWT (Access Token 2h + Refresh Token 7d)
- **授权**: RBAC 角色权限 + `@PreAuthorize` 方法级
- **多租户**: 请求头 `X-Tenant-Id` → MyBatis-Plus 自动注入 SQL
- **数据保护**: BCrypt 密码加密 + 敏感配置 jasypt + 数据脱敏
- **接口安全**: 幂等 Token + CORS + Sentinel 限流

## 7. 异步消息规范

| Exchange | Queue | Routing Key | 生产者 | 消费者 |
|----------|-------|-------------|--------|--------|
| expense.exchange | ai.review.queue | expense.report.submitted | expense-service | ai-service |
| expense.exchange | notification.event.queue | expense.result.notified | expense-service | notification-service |
| expense.exchange | notification.event.queue | ai.review.completed | ai-service | notification-service |

## 8. 审批流程

### 出差申请

```
DRAFT → 提交 → APPROVING
  → 经理审批 (candidateGroup=manager)
  → [金额>5000?] → 总监审批 (candidateGroup=director)
  → APPROVED / REJECTED
```

### 报销单

```
DRAFT → 提交 → APPROVING
  → 财务审核 (candidateGroup=finance)
  → 经理审批 (candidateGroup=manager)
  → APPROVED → 打款 → PAID
```
