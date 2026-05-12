# ExpenseFlow 差旅报销智能管理平台 — 设计文档

## 概述

覆盖出差申请 → 消费记录 → 报销提交 → 多级审批 → 财务打款的完整业务链路，融合 AI 能力。Spring Boot 3.3 + Spring Cloud 2023 + Vue 3。

## 用户角色

- 普通员工：提交出差申请、上传发票、填写报销单
- 审批人（动态）：审批/驳回、加签/转审
- 财务审核员：审核发票合规性、核对金额
- 出纳：执行打款、标记状态
- 租户管理员：管理组织架构、配置审批流程和费用政策
- 系统管理员（超管）：管理租户、全局配置、监控

## 服务拆分

| 服务 | 端口 | 职责 |
|------|:---:|------|
| gateway-service | 8080 | 统一网关、JWT 验签、Sentinel 限流、路由转发 |
| system-service | 8081 | 租户、用户/角色/权限(RBAC)、部门、OAuth2/SSO、多租户拦截器 |
| expense-service | 8082 | 出差申请、报销单、发票管理、消费记录、打款流水、费用政策 |
| approval-service | 8083 | Flowable 工作流、Drools 规则引擎、审批操作 |
| ai-service | 8084 | OCR 识别、DeepSeek 审单、RAG 问答、风控分析 |
| notification-service | 8085 | 钉钉机器人、站内消息、RabbitMQ 消费 |

## 技术选型

- 注册配置：Nacos 2.3，网关：Spring Cloud Gateway + Sentinel
- 工作流：Flowable 7，规则引擎：Drools 9
- AI：LangChain4j 0.35 + DeepSeek Chat API + 阿里云 OCR
- ORM：MyBatis-Plus 3.5（多租户拦截器），DB：MySQL 8（共享数据库 tenant_id 隔离）
- 缓存：Redis 7 + Caffeine，消息队列：RabbitMQ 3.13
- 可观测性：Prometheus + Grafana + SkyWalking 10
- 前端：Vue 3.4 + TypeScript + Element Plus + ECharts
- 部署：Docker Compose

## 多租户

共享数据库 + tenant_id 列隔离，通过请求头 X-Tenant-Id 透传，MyBatis-Plus TenantLineHandler 自动注入。

## 安全

Spring Security + JWT + BCrypt；操作审计 AOP；数据脱敏（Serializer）；接口幂等（Token）；jasypt 配置加密。

## 里程碑

- M1(3d)：基础设施搭建 — 6 服务骨架 + common + Docker Compose + DB schema
- M2(4d)：网关 + 系统服务 — 认证/多租户/RBAC
- M3(5d)：差旅报销服务 — CRUD + 发票 + 打款 + 费用政策
- M4(6d)：审批引擎 — Flowable + Drools + 审批链路由
- M5(4d)：AI 服务 + 通知 — OCR + DeepSeek + RAG + RabbitMQ
- M6(4d)：前端开发 + 全链路联调
- M7(2d)：Docker Compose 部署 + 演示准备

## 已生成的文件

- `CLAUDE.md` — 项目规范与协作指引
- `sql/init.sql` — 数据库 DDL（25 张表 + 种子数据）
