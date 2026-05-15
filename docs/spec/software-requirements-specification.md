# ExpenseFlow 软件需求规格说明书

**版本**：V1.0
**日期**：2026-05-14
**文档密级**：内部

---

## 修订记录

| 版本 | 日期 | 修订人 | 修订说明 |
|------|------|--------|----------|
| V1.0 | 2026-05-14 | 廖仙雁 | 初始版本，完整覆盖 6 个微服务需求 |

---

## 1. 引言

### 1.1 编写目的

本文档旨在定义 ExpenseFlow 差旅报销智能管理平台的全部功能需求与非功能需求，作为系统设计、开发、测试和验收的基线。

**目标受众**：
- 项目经理：评估工作量和制定里程碑计划
- 开发团队：理解功能边界，确认实现方案
- 测试团队：编写测试用例的依据
- 运维团队：了解部署架构和运行约束
- 产品负责人：验收交付物是否满足需求

### 1.2 产品范围

ExpenseFlow 是一套面向企业差旅报销管理的智能 SaaS 平台，覆盖“出差申请 → 消费记录 → 报销提交 → 多级审批 → 财务打款”全链路，深度融合 AI 能力（OCR 发票识别、DeepSeek 智能审单、RAG 政策问答、风控预警），支持多租户隔离和钉钉集成。

**核心价值主张**：
1. 一体化差旅报销流程管理，消除纸质单据和线下审批
2. AI 发票识别，自动化录入发票信息，减少人工录入错误
3. AI 审单辅助，自动校验报销合规性，标注风险等级
4. 智能政策问答，员工可自然语言查询差旅费用标准
5. 多级审批 + 规则引擎驱动，根据金额等条件动态路由审批节点
6. 钉钉实时推送审批通知，站内消息双通道通知

### 1.3 定义与缩略语

| 术语 | 说明 |
|------|------|
| OCR | Optical Character Recognition，光学字符识别，本系统用于自动提取发票上的关键字段 |
| RAG | Retrieval-Augmented Generation，检索增强生成，本系统用于结合费用政策知识库回答用户提问 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| JWT | JSON Web Token，无状态鉴权令牌 |
| Flowable | 开源工作流引擎，BPMN 2.0 标准 |
| Drools | 开源规则引擎，用于审批规则的条件决策 |
| DeepSeek | 大语言模型 API，用于智能审单和 RAG 问答 |
| 钉钉机器人 | 通过 Webhook 向企业钉钉群推送消息的自动化通知渠道 |

### 1.4 参考资料

| 文档 | 位置 | 说明 |
|------|------|------|
| ExpenseFlow 架构文档 | `docs/architecture.md` | 系统架构、微服务拓扑、数据库设计 |
| ExpenseFlow 演示脚本 | `docs/demo-script.md` | 功能演示流程 |
| ExpenseFlow 部署文档 | `docs/deployment.md` | Docker Compose 部署说明 |
| ExpenseFlow 用户启动指南 | `docs/user-startup-guide.md` | 本地开发环境启动指南 |
| CLAUDE.md | 项目根目录 | 项目编码规范、分支策略、调用规范 |
| 数据库初始化脚本 | `sql/init.sql` | 25 张业务表 DDL + 种子数据 |
| 阿里云 OCR API 文档 | https://help.aliyun.com/product/30410.html | 发票识别 API |
| DeepSeek API 文档 | https://api-docs.deepseek.com | Chat Completion API |
| Flowable 7 文档 | https://www.flowable.com/open-source/docs | BPMN 工作流引擎 |
| Drools 9 文档 | https://www.drools.org/learn/documentation.html | 规则引擎 |

---

## 2. 总体描述

### 2.1 产品视角

ExpenseFlow 采用前后端分离 + 微服务架构：

```
用户浏览器 ─── Nginx/Vite ─── Gateway (:8080) ───┬── system-service (:8081)   租户/用户/角色/权限/部门
                                                  ├── expense-service (:8082)  出差申请/报销/发票
                                                  ├── approval-service (:8083) 工作流/规则引擎
                                                  ├── ai-service (:8084)       OCR/DeepSeek/RAG/风控
                                                  └── notification-service (:8085) 站内消息/钉钉机器人
```

**微服务基础设施**：
- 注册发现 + 配置中心：Nacos 2.3
- 服务间同步调用：OpenFeign + Sentinel（熔断降级）
- 服务间异步消息：RabbitMQ 3.13
- 统一网关：Spring Cloud Gateway（JWT 验签、路由转发、CORS、限流）

### 2.2 用户角色特征

系统预置 6 种角色（RBAC 模型，定义于 `sys_role` 表）：

| 角色 | role_code | 核心特征 | 主要操作 |
|------|-----------|----------|----------|
| 超级管理员 | SUPER_ADMIN | 跨租户管理，系统级权限 | 租户管理、所有模块的配置和管理 |
| 租户管理员 | TENANT_ADMIN | 租户内最高权限 | 用户/角色/部门管理、费用政策配置 |
| 普通员工 | EMPLOYEE | 系统最大用户群体 | 出差申请、消费记录、报销提交、发票上传 |
| 审批人 | APPROVER | 部门经理/总监，审批任务人 | 待办审批、审批记录查询 |
| 财务审核员 | FINANCE | 财务部门，审核费用合规性 | 报销审核、发票验真审核 |
| 出纳 | CASHIER | 财务部门，执行打款操作 | 报销打款、打款流水查询 |

种子数据中还预设了 4 个演示用户（`manager`、`director`、`finance`、`cashier`），每个绑定对应角色。

### 2.3 运行环境

| 环境 | 规格要求 |
|------|----------|
| 操作系统 | Linux (生产) / Windows 11 / macOS (开发) |
| JDK | 17 |
| 数据库 | MySQL 8.0+ (UTF8MB4) |
| 缓存 | Redis 7 |
| 消息队列 | RabbitMQ 3.13 |
| 注册中心 | Nacos 2.3 |
| Node.js | 18+ (前端开发) |
| Docker | 24+ (容器化部署) |
| 浏览器 | Chrome 90+ / Edge 90+ / Firefox 90+ |

**推荐开发硬件**：16GB RAM、4 核 CPU、50GB SSD 可用空间（含 Docker 镜像和数据库）。

### 2.4 设计与实现约束

#### 2.4.1 技术选型约束

- 后端框架：Spring Boot 3.3 + Spring Cloud 2023，基于 JDK 17
- 前端框架：Vue 3.4 + TypeScript + Vite + Element Plus
- 架构模式：微服务（6 个独立服务），共享数据库（`expense_flow`）+ `tenant_id` 多租户隔离
- 工作流：Flowable 7（BPMN 2.0）+ Drools 9（规则引擎）
- ORM：MyBatis-Plus 3.5，禁止 `${}` SQL 拼接
- AI 集成：LangChain4j 0.35 + DeepSeek Chat API + 阿里云 OCR SDK

#### 2.4.2 编码规范约束

- Java 遵循阿里巴巴 Java 开发手册标准
- Controller 层只做三件事：接收参数 → 调用 Service → 返回结果，禁止在 Controller 中编写业务逻辑
- 请求/响应使用 DTO/VO 传输，禁止直接暴露 Entity
- Vue 前端统一使用 Composition API (`<script setup lang="ts">`)，禁止 Options API

#### 2.4.3 安全规范约束

- 密码：BCrypt 加密（强度 10）
- 认证：JWT (Access Token 2h / Refresh Token 7d)
- 敏感配置：jasypt 加密，密钥通过环境变量注入
- 数据脱敏：手机号（138\*\*\*\*1234）、银行卡号（仅后四4位）、身份证号
- 文件上传：仅允许 PNG/JPG/PDF，≤ 10MB
- 接口幂等：创建/提交类接口使用 Token 机制

#### 2.4.4 兼容性约束

- 支持主流浏览器：Chrome 90+、Edge 90+、Firefox 90+
- API 向后兼容：新增字段不影响旧版客户端
- 数据库字段新增要求有默认值

---

## 3. 功能需求

### 3.1 系统服务（system-service）

#### 3.1.1 用户认证

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-001 | 用户登录：`POST /system/auth/login`，用户名 + 密码登录，返回 JWT Access Token 和 Refresh Token | P0 |
| SYS-002 | 用户登出：`POST /system/auth/logout`，Token 加入 Redis 黑名单，有效期对齐 Token 过期时间 | P0 |
| SYS-003 | Token 刷新：`POST /system/auth/refresh`，刷新即将过期的 Access Token | P1 |
| SYS-004 | 获取当前用户信息：`GET /system/auth/me`，返回当前登录用户的角色、权限列表（用于前端菜单渲染） | P0 |

**业务规则**：
- Access Token 有效期 2 小时，Refresh Token 有效期 7 天
- 登录失败 5 次后账号锁定 30 分钟
- Token 校验在 Gateway 层完成，无效 Token 直接拒绝请求

#### 3.1.2 用户管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-005 | 分页查询用户列表：`GET /system/user/page`，支持关键字搜索（用户名/姓名/手机号） | P1 |
| SYS-006 | 查询单个用户：`GET /system/user/{id}` | P1 |
| SYS-007 | 创建用户：`POST /system/user`，创建租户内用户，密码 BCrypt 加密 | P0 |
| SYS-008 | 编辑用户：`PUT /system/user/{id}` | P1 |
| SYS-009 | 删除用户：`DELETE /system/user/{id}`，逻辑删除 | P1 |
| SYS-010 | 启用/禁用用户：`PATCH /system/user/{id}/status` | P1 |
| SYS-011 | 重置密码：`PATCH /system/user/{id}/password` | P1 |

**业务规则**：
- 用户名在租户内唯一（`uk_username_tenant`）
- 密码 BCrypt 加密，不存储明文
- 删除操作为逻辑删除（`deleted=1`），数据不可恢复但可通过 SQL 找回
- 禁用用户无法登录，已签发的 Token 自动失效

#### 3.1.3 角色与权限管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-012 | 角色管理（CRUD）：角色编码、角色名称、角色类型（系统内置/租户自定义） | P1 |
| SYS-013 | 权限管理（菜单 + 按钮 + API 三级）：权限标识如 `expense:report:create` | P1 |
| SYS-014 | 用户-角色关联：为用户分配角色（多对多） | P1 |
| SYS-015 | 角色-权限关联：为角色分配权限（多对多） | P1 |

**权限标识规范**：`{服务}:{模块}:{操作}`，例如：
- `expense:travel:create` — 创建出差申请
- `expense:report:submit` — 提交报销单
- `expense:report:delete` — 删除报销单
- `approval:task:complete` — 处理审批任务
- `system:user:create` — 创建用户

**业务规则**：
- 系统内置角色（`role_type=1`）不可删除，不可修改角色编码
- 租户自定义角色（`role_type=2`）仅在当前租户内生效
- 权限与角色关联时，只需分配叶子节点权限

#### 3.1.4 部门与员工管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-016 | 部门管理（树形结构 CRUD）：`/system/department/**` | P1 |
| SYS-017 | 员工管理：关联用户与部门、工号、职位 (`sys_employee`) | P1 |

**业务规则**：
- 部门采用父子结构（`parent_id`），支持无限层级
- 一个用户只能关联一个部门（`uk_user_tenant`）
- 部门负责人（`leader_id`）必须是已存在的用户

#### 3.1.5 租户管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-018 | 租户 CRUD：租户编码、名称、联系人、联系手机 | P1 |
| SYS-019 | 租户启用/禁用：禁用后租户内所有用户无法登录 | P1 |
| SYS-020 | 租户过期时间：到期后自动禁止访问 | P2 |

**业务规则**：
- 租户编码全局唯一（`uk_tenant_code`）
- 系统默认租户（tenant_id=0）不可删除
- SaaS 模式下每个租户数据完全隔离（通过 `tenant_id` 列）

#### 3.1.6 审计与字典

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| SYS-021 | 操作审计日志自动记录（`sys_audit_log`）：通过 `@AuditLog` 注解自动记录 CRUD 操作、操作人、IP、耗时、变更前后值 | P1 |
| SYS-022 | 字典管理（`sys_dict_type` + `sys_dict_data`）：费用类型、发票类型、审批状态等枚举值管理 | P1 |

**审计日志记录内容**：
- 操作人（用户 ID + 用户名）
- 操作类型（INSERT / UPDATE / DELETE / SUBMIT / WITHDRAW / PAY 等）
- 操作模块（出差申请、报销单、打款管理 等）
- 目标对象类型和 ID
- 请求参数（脱敏后）、变更前后值（UPDATE 场景）
- 请求 IP、User-Agent
- 执行耗时（毫秒）

---

### 3.2 差旅报销服务（expense-service）

#### 3.2.1 出差申请

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-001 | 分页查询出差申请列表：`GET /expense/travel/page`，支持按状态筛选 | P0 |
| EX-002 | 查看申请详情：`GET /expense/travel/{id}`，包含出差日期、目的地、预估费用、审批记录 | P0 |
| EX-003 | 新建出差申请：`POST /expense/travel`，填写出差目的、目的地、起止日期、预估金额、同行人 | P0 |
| EX-004 | 编辑出差申请：`PUT /expense/travel/{id}`，仅 DRAFT/WITHDRAWN 状态可编辑 | P0 |
| EX-005 | 删除出差申请：`DELETE /expense/travel/{id}`，仅 DRAFT 状态可删除 | P1 |
| EX-006 | 提交审批：`POST /expense/travel/{id}/submit`，状态变为 SUBMITTED → 启动 Flowable 审批流程 | P0 |
| EX-007 | 撤回申请：`POST /expense/travel/{id}/withdraw`，DRAFT/SUBMITTED 状态可撤回 | P1 |

**业务规则**：
- 业务编号自动生成格式：`TR-YYYYMMDD-XXXX`（TR = Travel Request）
- 结束日期必须晚于开始日期
- 提交后状态流转：DRAFT → SUBMITTED → APPROVING → APPROVED / REJECTED
- 撤回操作仅对 DRAFT 和 SUBMITTED 状态有效，APPROVING 状态不允许撤回
- 关联审批流程实例（`process_instance_id`），提交时调用 `POST /approval/process/start` 创建流程

**出差申请状态机**：

```
[DRAFT] ──submit──> [SUBMITTED] ──审批中──> [APPROVING]
   │                     │                       │
   │<───withdraw─────────┘                ┌──────┴──────┐
   │                                      ▼              ▼
   └───delete                          [APPROVED]    [REJECTED]
                                          │              │
                                          └───withdraw────┘
```

#### 3.2.2 报销单管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-008 | 分页查询报销单列表：`GET /expense/report/page`，支持按状态筛选 | P0 |
| EX-009 | 查看报销单详情：`GET /expense/report/{id}`，包含报销明细、发票信息、审批记录 | P0 |
| EX-010 | 新建报销单：`POST /expense/report`，填写报销日期，可关联出差申请 | P0 |
| EX-011 | 编辑报销单：`PUT /expense/report/{id}`，仅 DRAFT 状态可编辑 | P0 |
| EX-012 | 删除报销单：`DELETE /expense/report/{id}`，仅 DRAFT 状态可删除 | P1 |
| EX-013 | 提交审批：`POST /expense/report/{id}/submit`，启动 Flowable 审批流程（财务审核 → 经理审批） | P0 |
| EX-014 | 撤回报销单：`POST /expense/report/{id}/withdraw` | P1 |

**业务规则**：
- 业务编号自动生成格式：`ER-YYYYMMDD-XXXX`（ER = Expense Report）
- `total_amount`（报销总额）= 所有明细项金额之和
- `actual_amount`（实报金额）由财务审核后确定，可能剪减
- 报销单可关联出差申请（`travel_request_id`），非强制
- 状态流转：DRAFT → SUBMITTED → APPROVING → APPROVED → PAID

**报销单状态机**：

```
[DRAFT] ──submit──> [SUBMITTED] ──审批中──> [APPROVING]
   │                     │                       │
   │<───withdraw─────────┘                ┌──────┴──────┐
   │                                      ▼              ▼
   └───delete                          [APPROVED]    [REJECTED]
                                           │              │
                                        pay操作           │
                                           │              │
                                           ▼              │
                                         [PAID] ───withdraw────────┘
```

#### 3.2.3 报销明细

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-015 | 查询报销明细列表：`GET /expense/report/{reportId}/item/list` | P0 |
| EX-016 | 添加报销明细：`POST /expense/report/{reportId}/item` | P0 |
| EX-017 | 删除报销明细：`DELETE /expense/report/{reportId}/item/{itemId}` | P1 |

**费用类型枚举**：`TRANSPORT`（交通费）、`HOTEL`（住宿费）、`MEAL`（餐费）、`OTHER`（其他）

**业务规则**：
- 每次添加明细后自动更新报销单的 `total_amount`
- 明细项可关联发票（`invoice_id`），先 OCR 识别再关联

#### 3.2.4 发票管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-018 | 上传发票：`POST /expense/invoice/upload`，支持 PNG/JPG/PDF，≤ 10MB | P0 |
| EX-019 | 分页查看发票列表：`GET /expense/invoice/page`，支持按 OCR 状态筛选 | P0 |
| EX-020 | 查看发票详情：`GET /expense/invoice/{id}` | P1 |
| EX-021 | 触发 OCR 识别：`POST /expense/invoice/{id}/ocr`，异步调用 AI 服务识别发票 | P0 |

**发票类型**：
- `VAT_SPECIAL`：增值税专用发票
- `VAT_NORMAL`：增值税普通发票
- `ELECTRONIC`：电子发票

**业务规则**：
- 文件类型校验：仅允许 PNG、JPG、PDF
- 文件大小校验：≤ 10MB
- OCR 状态流转：PENDING → PROCESSING → SUCCESS / FAILED
- OCR 识别完成后，通过 OpenFeign 或 RabbitMQ 回写解析结果到 `ex_invoice` 和 `ai_ocr_result` 两张表

#### 3.2.5 消费记录

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-022 | 分页查询消费记录：`GET /expense/cost/page`，按当前用户过滤 | P1 |
| EX-023 | 新增消费记录：`POST /expense/cost` | P1 |
| EX-024 | 编辑消费记录：`PUT /expense/cost/{id}` | P1 |
| EX-025 | 删除消费记录：`DELETE /expense/cost/{id}` | P1 |

**业务规则**：
- 消费记录可关联发票（OCR 后自动创建）、出差申请、报销单
- 报销单提交后，其关联的消费记录不可再编辑

#### 3.2.6 打款管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-026 | 分页查询打款流水：`GET /expense/payment/page` | P0 |
| EX-027 | 执行打款：`POST /expense/payment/pay?reportId={id}`，仅出纳角色可操作，报销单状态必须为 APPROVED | P0 |

**业务规则**：
- 打款流水号自动生成：`PY-YYYYMMDD-XXXX`
- 报销单必须在 APPROVED 状态才能打款
- 打款成功后报销单状态变更为 PAID，记录 `paid_time` 和 `paid_amount`
- 打款操作需要审计日志（`@AuditLog`）
- 打款方式默认 `BANK_TRANSFER`（银行转账）

#### 3.2.7 费用政策

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-028 | 查询费用政策列表：`GET /expense/policy/list` | P1 |
| EX-029 | 新增费用政策：`POST /expense/policy`，设置费用类型、单次上限、日限额、适用城市等级 | P1 |
| EX-030 | 编辑费用政策：`PUT /expense/policy/{id}` | P1 |
| EX-031 | 删除费用政策：`DELETE /expense/policy/{id}` | P1 |

**城市等级**：`TIER1`（一线城市）、`TIER2`（二线城市）、`TIER3`（三线及以下）

**默认费用政策（种子数据）**：
- 交通费标准：单次上限 5000 元
- 住宿费标准（一线）：日限额 500 元
- 住宿费标准（其他）：日限额 350 元
- 餐费补助：日限额 100 元

#### 3.2.8 审批回调接收

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| EX-032 | 审批结果回调：`PUT /expense/callback/approval-result`，审批服务审批完成后回调更新业务单据状态 | P0 |

**业务规则**：
- 审批通过时：更新业务单据状态为 APPROVED
- 审批驳回时：更新业务单据状态为 REJECTED
- 通过 RabbitMQ 发送 `expense.result.notified` 事件至 notification-service

---

### 3.3 审批引擎服务（approval-service）

#### 3.3.1 流程管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AP-001 | 启动审批流程：`POST /approval/process/start`，用于 Feign 内部调用（expense-service 提交出差申请/报销单时），创建 Flowable 流程实例 | P0 |
| AP-002 | 审批结果回调：`PUT /approval/process/callback/result`，流程结束后自动回调业务方更新状态 | P0 |

**审批流程定义**：

1. **出差申请审批流程**：
```
START → 经理审批 (candidateGroup=manager)
     → [金额>5000?] → 总监审批 (candidateGroup=director)
     → END → 状态 APPROVED / REJECTED
```

2. **报销单审批流程**：
```
START → 财务审核 (candidateGroup=finance)
     → 经理审批 (candidateGroup=manager)
     → END → 状态 APPROVED → 待打款
```

**业务规则**：
- 流程定义由 Flowable BPMN 文件描述
- 候选组（candidateGroup）对应角色 code：`manager`、`director`、`finance`
- Drools 规则引擎负责金额阈值判断（>5000 元自动添加总监审批节点）
- 所有审批记录双写到 `ap_approval_record` 表，便于报表查询

#### 3.3.2 任务处理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AP-003 | 查询待办任务：`GET /approval/task/page`，按候选组（candidateGroup）或审批人（assignee）筛选 | P0 |
| AP-004 | 完成任务（审批通过/驳回）：`POST /approval/task/{taskId}/complete`，提交审批意见和动作 | P0 |
| AP-005 | 委托任务：`POST /approval/task/{taskId}/delegate?delegateToUser={userId}`，将审批任务转交给他人 | P1 |
| AP-006 | 查看审批记录：`GET /approval/task/record/list?businessType=&businessId=`，按业务类型和业务 ID 查询完整审批历史 | P0 |

**审批动作枚举**：
- `APPROVE`：审批通过，流程进入下一节点
- `REJECT`：驳回，流程终止
- `DELEGATE`：委托他人审批
- `ADD_SIGN`：加签
- `RETURN`：退回上一节点

**业务规则**：
- 驳回操作需要填写驳回原因（`comment` 字段）
- 任务委托后，原审批人无权再操作该任务
- 审批记录包含审批人、审批时间、审批动作、审批意见

---

### 3.4 AI 智能服务（ai-service）

#### 3.4.1 OCR 发票识别

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AI-001 | 发票 OCR 识别：`POST /ai/ocr/recognize`，调用阿里云 OCR API 识别发票图片，返回结构化字段 | P0 |
| AI-002 | 查询 OCR 识别结果：`GET /ai/ocr/{id}` | P1 |

**OCR 识别字段**（阿里云 OCR API 返回值）：
- 发票号码 (`invoice_no`)
- 发票代码 (`invoice_code`)
- 开票日期 (`invoice_date`)
- 金额（不含税）：`amount`
- 税额：`tax_amount`
- 价税合计：`total_amount`
- 销售方名称：`seller_name`
- 销售方税号：`seller_tax_no`
- 购买方名称：`buyer_name`
- 购买方税号：`buyer_tax_no`

**业务规则**：
- OCR 结果存储到 `ai_ocr_result` 表，同时回写至 `ex_invoice` 表的关键字段
- 置信度（`confidence`）低于 0.85 的字段标记为待人工确认
- 记录 API 调用耗时（`process_time_ms`）用于性能监控
- 识别失败时记录 `error_message` 和 `FAILED` 状态
- 使用阿里云 AppCode 认证方式

#### 3.4.2 DeepSeek 智能审单

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AI-003 | AI 审单评估：`POST /ai/review/evaluate`，调用 DeepSeek Chat API 对报销单进行合规性审查 | P0 |
| AI-004 | 风险分析：`POST /ai/review/risk?reportId={id}`，对指定报销单进行深度风险分析 | P1 |

**审单评估维度**：
1. 费用类型与费用政策的匹配度
2. 金额是否超出政策上限
3. 发票信息是否完整、一致
4. 发票金额与报销金额是否一致
5. 是否存在重复报销嫌疑（同一发票多次使用）

**审单结果**（`review_result` 字段）：
- `APPROVED`：AI 建议通过
- `REVIEW_NEEDED`：AI 建议人工复核
- `REJECTED`：AI 建议驳回

**风险等级**（`risk_level` 字段）：
- `LOW`：低风险，可自动通过
- `MEDIUM`：中风险，建议人工复核
- `HIGH`：高风险，建议驳回 + 人工审查

**业务规则**：
- 大模型调用记录 Token 消耗（`prompt_tokens`、`completion_tokens`）
- 审单结果存储到 `ai_review_result` 表
- 通过 RabbitMQ 发送 `ai.review.completed` 事件至 notification-service
- 支持统计 AI 建议采纳率（`ai_confidence_stats` 表）

#### 3.4.3 RAG 智能问答

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AI-005 | 政策问答：`POST /ai/rag/ask`，用户自然语言提问，基于费用政策知识库检索增强生成回答 | P1 |

**知识库内容**：
- 费用政策（`ex_expense_policy` 表中的所有活跃政策）
- 报销流程说明
- 发票要求说明

**示例问答**：
- 问："住宿费报销标准是多少？"
- 答："根据公司差旅政策，一线城市住宿费日报销上限为 500 元，二线城市为 350 元，三线及以下城市为 350 元。"
- 问："出差申请需要提前多久提交？"
- 答："建议在出差日期前至少 3 个工作日提交出差申请，确保有足够的审批时间。"

#### 3.4.4 AI 置信度统计

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| AI-006 | AI 审单效果统计（`ai_confidence_stats` 表）：按日统计审单总数、自动通过数、人工通过数、驳回数、AI 建议采纳/推翻数 | P2 |

---

### 3.5 通知服务（notification-service）

#### 3.5.1 站内消息

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| NT-001 | 查看消息列表：`GET /notification/message/page`，按当前用户分页查询消息 | P0 |
| NT-002 | 标记单条已读：`PUT /notification/message/{id}/read` | P0 |
| NT-003 | 全部标记已读：`PUT /notification/message/read-all` | P0 |
| NT-004 | 查询未读消息数：`GET /notification/message/unread-count` | P1 |

**消息类型**：
- `SYSTEM`：系统通知
- `APPROVAL`：审批通知（待办 / 结果）
- `NOTIFICATION`：普通通知

**业务规则**：
- 消息创建后默认未读（`is_read=0`）
- 标记已读时记录 `read_time`
- 接收人只能查看自己的消息（按 `user_id` 过滤）

#### 3.5.2 钉钉机器人推送

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| NT-005 | 钉钉机器人 Webhook 推送：通过 RabbitMQ 消费事件，将审批结果、AI 审单结果推送到钉钉群 | P0 |

**钉钉推送规则**：
- 群消息使用 Markdown 格式
- 支持 Mock 模式（`dingtalk.mock=true`），开发阶段不实际调用 Webhook
- 发送失败时本地重试 3 次，超过后记录异常日志

**推送模板**（种子数据中的 3 个模板）：

| 模板编码 | 模板名称 | 通道 |
|----------|----------|------|
| APPROVAL_PENDING | 审批待办通知 | DINGTALK |
| APPROVAL_RESULT | 审批结果通知 | DINGTALK |
| AI_REVIEW_DONE | AI 审单完成 | IN_APP |

**模板变量**：`{申请人}`、`{业务类型}`、`{单据编号}`、`{金额}`、`{审批人}`、`{审批意见}`、`{审批时间}`、`{审批结果}`、`{结果}`、`{风险等级}`

#### 3.5.3 通知模板管理

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| NT-006 | 查看通知模板列表：`GET /notification/template/list` | P1 |
| NT-007 | 查看单个模板：`GET /notification/template/{code}` | P1 |
| NT-008 | 新增模板：`POST /notification/template` | P1 |
| NT-009 | 编辑模板：`PUT /notification/template/{id}` | P1 |

---

### 3.6 RabbitMQ 异步消息

#### 3.6.1 消息架构

| Exchange | Queue | Routing Key | 生产者 | 消费者 |
|----------|-------|-------------|--------|--------|
| `expense.exchange` | `notification.event.queue` | `expense.result.notified` | expense-service（审批回调） | notification-service |
| `expense.exchange` | `notification.event.queue` | `ai.review.completed` | ai-service（审单完成） | notification-service |

**消息体规范**（JSON 格式）：
```json
{
  "eventId": "UUID-用于去重",
  "eventType": "approval.result | ai.review.completed",
  "businessType": "TRAVEL_REQUEST | EXPENSE_REPORT",
  "businessId": 12345,
  "outcome": "APPROVED | REJECTED",
  "tenantId": 0,
  "applicantId": 2
}
```

**业务规则**：
- 每条消息包含唯一 `eventId`（UUID），消费端基于此去重
- 消费端处理失败不自动重试（避免重复推送钉钉），记录异常日志
- 消息使用 Jackson2Json 序列化（`Jackson2JsonMessageConverter`）

---

### 3.7 工作台与数据看板

| 需求编号 | 功能 | 优先级 |
|----------|------|:------:|
| DS-001 | 数据看板（Dashboard）：统计卡片展示出差申请数、报销单数、待审批数、打款金额 | P0 |
| DS-002 | ECharts 饼图：按费用类型展示费用分布（交通/住宿/餐费/其他） | P0 |
| DS-003 | ECharts 柱状图：按状态展示审批统计（待审批/已通过/已驳回） | P0 |

---

## 4. 非功能需求

### 4.1 性能需求

| 指标 | 目标值 | 备注 |
|------|--------|------|
| 页面首次加载时间 | ≤ 2 秒 | Dashboard 等关键页面 |
| API 响应时间（P95） | ≤ 500ms | 查询类接口；不含 AI 调用 |
| OCR 识别耗时 | ≤ 3 秒 | 阿里云 OCR API 远端调用 |
| AI 审单耗时 | ≤ 10 秒 | DeepSeek API 远端调用 |
| 并发用户数 | 100+ | 单租户峰值 |
| Gateway 限流 | 100 req/s/用户 | Sentinel 限流规则 |
| 数据库连接池 | 20 连接/服务 | HikariCP 默认 |
| 缓存命中率 | ≥ 80% | Redis + Caffeine 二级缓存 |

### 4.2 安全性需求

| 需求编号 | 安全领域 | 具体措施 |
|:---------|----------|----------|
| SEC-01 | 认证 | JWT 无状态认证（Access Token 2h + Refresh Token 7d），登出 Token 加入 Redis 黑名单 |
| SEC-02 | 授权 | RBAC 角色权限 + `@PreAuthorize` 方法级权限校验，如 `@PreAuthorize("@pms.hasPermission('expense:report:delete')")` |
| SEC-03 | 多租户隔离 | 请求头 `X-Tenant-Id` 传递 → MyBatis-Plus TenantLineHandler 自动注入 SQL `WHERE tenant_id = ?` |
| SEC-04 | 密码安全 | BCrypt 加密（Spring Security 默认强度 10），不存储明文 |
| SEC-05 | 敏感配置保护 | 数据库密码、API Key 使用 jasypt 加密，密钥通过环境变量 `JASYPT_ENCRYPTOR_PASSWORD` 注入 |
| SEC-06 | 数据脱敏 | VO 层 `@Sensitive` 注解序列化脱敏：手机号（138\*\*\*\*1234）、银行卡号（仅后 4 位）、身份证号 |
| SEC-07 | SQL 注入防护 | 禁止 MyBatis `${}` 拼接，仅使用 `#{}` 参数化查询 |
| SEC-08 | 文件上传安全 | 仅允许 PNG/JPG/PDF，大小 ≤ 10MB，文件存储在非 web 根目录，文件名 UUID 重命名 |
| SEC-09 | CORS 防护 | Gateway 层统一配置允许域名，生产环境禁止 `*` 通配符 |
| SEC-10 | 接口幂等 | 创建/提交类接口使用幂等 Token 机制（先获取 Token → 请求携带 → 消费后 Redis 删除） |
| SEC-11 | 操作审计 | 敏感操作（提交审批、打款、用户 CRUD）自动记录审计日志，含操作人/IP/时间/变更内容 |
| SEC-12 | CSRF | 前后端分离 + JWT 无 Cookie，天然免疫 CSRF 攻击 |

### 4.3 可用性需求

| 指标 | 目标值 |
|------|--------|
| 系统整体可用性 | 99.5% |
| 数据库可用性 | 99.9%（MySQL 主从复制） |
| 服务重启恢复时间 | ≤ 30 秒 |
| 数据备份频率 | 每日全量备份 + 每 6 小时增量备份 |
| 服务熔断 | Sentinel 熔断：错误率 > 50% 自动降级 |
| Feign 降级 | 全部 Feign 接口配置 `fallbackFactory`，返回默认值或友好错误提示 |

### 4.4 可维护性需求

| 需求编号 | 要求 | 实现 |
|----------|------|------|
| MT-01 | 健康检查 | `/actuator/health` 端口就绪探针 |
| MT-02 | 指标采集 | Micrometer → Prometheus → Grafana |
| MT-03 | 链路追踪 | SkyWalking 10 |
| MT-04 | 日志规范 | Logback + JSON 格式，ELK 采集 |
| MT-05 | 配置热更新 | Nacos 配置中心修改后实时生效（`@RefreshScope`） |
| MT-06 | API 文档 | Knife4j 自动生成（OpenAPI 3.0） |
| MT-07 | 代码规范 | 阿里巴巴 Java 手册 + Prettier（前端）+ Spotless Maven Plugin（后端） |

### 4.5 可扩展性需求

- 新增费用类型：仅需在 `sys_dict_data` 添加字典值 + `ex_expense_policy` 添加对应政策
- 新增审批节点：修改 BPMN 流程定义（BPMN XML 文件），无需改代码
- 新增通知通道：在 `nt_notification_template` 添加新模板 + 消息枚举增加渠道类型
- 新增 AI 场景：ai-service 新增独立 Controller/Service，不耦合现有功能

---

## 5. 外部接口需求

### 5.1 阿里云 OCR API

| 属性 | 说明 |
|------|------|
| 接口名称 | 增值税发票识别（RecognizeInvoice） |
| 请求方式 | HTTPS POST |
| 接口地址 | `https://ocrapi-advanced.aliyuncs.com/` |
| 认证方式 | AppCode（通过请求头 `Authorization: APPCODE {code}` 传递） |
| 输入格式 | Base64 编码的发票图片（PNG/JPG/PDF） |
| 输出格式 | JSON：发票号码、发票代码、开票日期、金额、税额、价税合计、销售方/购买方信息、置信度 |
| 超时时间 | 10 秒 |
| 调用位置 | `ai-service` 的 `OcrService` |

**处理逻辑**：
1. `expense-service` 上传发票后，保存至 `ex_invoice` 表（`ocr_status=PENDING`）
2. 调用 `POST /ai/ocr/recognize` 转发至 ai-service
3. ai-service 调用阿里云 OCR API，解析响应
4. 结果写入 `ai_ocr_result` 表，并回写 `ex_invoice` 表的关键字段
5. 更新 `ocr_status` 为 `SUCCESS` 或 `FAILED`

### 5.2 DeepSeek Chat API

| 属性 | 说明 |
|------|------|
| 接口名称 | DeepSeek Chat Completion API |
| 请求方式 | HTTPS POST |
| 接口地址 | `https://api.deepseek.com/v1/chat/completions` |
| 认证方式 | API Key（请求头 `Authorization: Bearer {key}`） |
| 模型 | `deepseek-chat` |
| 调用场景 | AI 审单（`POST /ai/review/evaluate`）+ RAG 问答（`POST /ai/rag/ask`） |
| 超时时间 | 60 秒（大模型推理耗时较长） |
| 调用位置 | ai-service 的 `DeepSeekReviewService` 和 `RagService` |

### 5.3 钉钉机器人 Webhook

| 属性 | 说明 |
|------|------|
| 推送方式 | HTTPS POST |
| Webhook URL | 钉钉群机器人 Webhook 地址（可配置） |
| 认证方式 | 签名校验（加签模式，`dingtalk.secret`）或关键字 |
| 消息格式 | Markdown（钉钉支持） |
| 推送时机 | RabbitMQ 消费到通知事件后自动推送 |
| Mock 模式 | `dingtalk.mock=true` 时仅打印日志，不实际发送 |
| 调用位置 | notification-service 的 `DingTalkService` |

### 5.4 Nacos 注册与配置中心

| 属性 | 说明 |
|------|------|
| 注册中心地址 | `localhost:8848`（开发）/ `nacos:8848`（Docker） |
| 命名空间 | `expenseflow` |
| 分组 | `DEFAULT_GROUP` |
| 服务注册 | 各微服务 `spring.application.name` 注册为服务名 |

---

## 6. 数据需求概要

### 6.1 数据库设计

数据库名：`expense_flow`
字符集：`utf8mb4` / 排序规则：`utf8mb4_unicode_ci`
多租户隔离策略：共享数据库 + `tenant_id` 列隔离（每个业务表均包含 `tenant_id` 字段）

### 6.2 核心数据表

| 服务 | 表前缀 | 表数量 | 核心表 |
|------|--------|:------:|--------|
| system-service | `sys_` | 12 | `sys_tenant`、`sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_department`、`sys_employee`、`sys_dict_type`、`sys_dict_data`、`sys_audit_log`、`sys_oauth_user` |
| expense-service | `ex_` | 7 | `ex_travel_request`、`ex_expense_report`、`ex_expense_item`、`ex_invoice`、`ex_cost_record`、`ex_payment_record`、`ex_expense_policy` |
| approval-service | `ap_` | 1 (+ 60+ Flowable) | `ap_approval_record`（业务维度双写） |
| ai-service | `ai_` | 3 | `ai_ocr_result`、`ai_review_result`、`ai_confidence_stats` |
| notification-service | `nt_` | 2 | `nt_message`、`nt_notification_template` |

Flowable 工作流引擎 60+ 张表（`act_*`、`flw_*` 前缀）由框架自动建表。

### 6.3 关键数据字典

**业务单据状态**（`status` 字段）：
| 值 | 含义 |
|------|------|
| DRAFT | 草稿，可编辑/删除 |
| SUBMITTED | 已提交，等待审批 |
| APPROVING | 审批中进行中 |
| APPROVED | 审批通过 |
| REJECTED | 已驳回 |
| WITHDRAWN | 已撤回 |
| CHANGED | 已变更（出差申请变更） |
| PAID | 已打款（仅报销单） |

**费用类型**：
| 值 | 含义 |
|------|------|
| TRANSPORT | 交通费 |
| HOTEL | 住宿费 |
| MEAL | 餐费 |
| OTHER | 其他费用 |

**OCR 状态**：
| 值 | 含义 |
|------|------|
| PENDING | 待识别 |
| PROCESSING | 识别中 |
| SUCCESS | 识别成功 |
| FAILED | 识别失败 |

**发票验真状态**：
| 值 | 含义 |
|------|------|
| PENDING | 待验真 |
| VERIFIED | 已验证 |
| INVALID | 无效发票 |

**打款状态**：
| 值 | 含义 |
|------|------|
| PENDING | 待打款 |
| PROCESSING | 打款中 |
| SUCCESS | 打款成功 |
| FAILED | 打款失败 |

**用户状态**：
| 值 | 含义 |
|------|------|
| 1 | 启用 |
| 0 | 禁用 |

### 6.4 数据保留策略

- 逻辑删除（`deleted` 字段）：用户表、租户表、角色表、权限表、部门表、员工表、费用政策表
- 物理删除（直接 DELETE）：报销明细项（关联报销单主体，随报销单删除）
- 审计日志保留：至少 2 年，可配置定期归档
- 发票图片文件：保留至对应报销单归档后 1 年

### 6.5 缓存策略

| 缓存内容 | 缓存类型 | TTL | 失效策略 |
|----------|----------|-----|----------|
| 用户权限列表 | Caffeine（本地） | 30 分钟 | 角色变更时主动失效 |
| 字典数据 | Redis | 1 小时 | 字典更新时主动失效 |
| 费用政策 | Redis + Caffeine | 1 小时 | 政策更新时主动失效 |
| Token 黑名单 | Redis | 对齐 Token 过期 | 到期自动删除 |
| 幂等 Token | Redis | 5 分钟 | 消费后立即删除 |

---

## 7. 附录

### 7.1 服务端口清单

| 服务 | HTTP 端口 | 内部端口（Docker） |
|------|:---------:|:------------------:|
| gateway-service | 8080 | 8080 |
| system-service | 8081 | 8081 |
| expense-service | 8082 | 8082 |
| approval-service | 8083 | 8083 |
| ai-service | 8084 | 8084 |
| notification-service | 8085 | 8085 |
| Nacos | 8848 | 8848 |
| MySQL | 3306 | 3306 |
| Redis | 6379 | 6379 |
| RabbitMQ | 5672 / 15672 | 5672 / 15672 |

### 7.2 环境变量清单

| 变量名 | 说明 | 服务 |
|--------|------|------|
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` | 数据库连接信息 | 全部 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 连接信息 | 全部 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASS` | RabbitMQ 连接信息 | expense-service、notification-service |
| `NACOS_SERVER_ADDR` | Nacos 地址 | 全部 |
| `JASYPT_ENCRYPTOR_PASSWORD` | jasypt 解密密钥 | 全部 |
| `ALIBABA_CLOUD_APP_CODE` | 阿里云 OCR AppCode | ai-service |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | ai-service |
| `DINGTALK_WEBHOOK_URL` | 钉钉机器人 Webhook | notification-service |
| `DINGTALK_SECRET` | 钉钉机器人签名密钥 | notification-service |

### 7.3 项目模块清单

```
ExpenseFlow/
├── expense-common/              # 公共模块（统一响应、异常处理、BaseController、注解、工具类）
├── gateway-service/             # 网关服务 (:8080)
├── system-service/              # 系统服务 (:8081)
├── expense-service/             # 报销服务 (:8082)
├── approval-service/            # 审批服务 (:8083)
├── ai-service/                  # AI 服务 (:8084)
├── notification-service/        # 通知服务 (:8085)
├── expense-web/                 # Vue 3 前端
├── sql/                         # 数据库初始化脚本
├── docs/                        # 文档
├── docker-compose.yml           # 中间件编排
└── docker-compose.services.yml  # 微服务编排
```

### 7.4 审批流程决策矩阵

| 条件 | 审批节点 |
|------|----------|
| 出差金额 ≤ 5000 元 | 经理审批（单级） |
| 出差金额 > 5000 元 | 经理审批 → 总监审批（两级） |
| 报销单（全部） | 财务审核 → 经理审批（两级） |
| AI 审单风险等级 = LOW | 建议自动通过 |
| AI 审单风险等级 = MEDIUM | 建议人工复核 |
| AI 审单风险等级 = HIGH | 建议驳回 |

### 7.5 用户界面路由

| 路由路径 | 页面名称 | 对应服务 | 角色权限 |
|----------|----------|----------|----------|
| `/login` | 登录页 | system-service | 无限制 |
| `/dashboard` | 数据看板 | 多服务聚合 | 登录用户 |
| `/travel` | 出差申请列表 | expense-service | EMPLOYEE+ |
| `/travel/create` | 新建出差申请 | expense-service | EMPLOYEE+ |
| `/travel/:id/edit` | 编辑出差申请 | expense-service | EMPLOYEE+ |
| `/report` | 报销单列表 | expense-service | EMPLOYEE+ |
| `/report/create` | 新建报销单 | expense-service | EMPLOYEE+ |
| `/report/:id/edit` | 编辑报销单 | expense-service | EMPLOYEE+ |
| `/invoice` | 发票管理 | expense-service + ai-service | EMPLOYEE+ |
| `/approval` | 审批工作台 | approval-service | APPROVER / FINANCE |
| `/ai-review` | AI 审单 | ai-service | FINANCE+ |
| `/ai-assistant` | AI 智能助手 | ai-service | 登录用户 |
| `/notification` | 消息通知 | notification-service | 登录用户 |

### 7.6 微服务间调用关系

| 调用方 | 被调用方 | 调用方式 | 接口 |
|--------|----------|----------|------|
| expense-service | approval-service | OpenFeign | `POST /approval/process/start` |
| expense-service | system-service | OpenFeign | `GET /system/user/{id}`、`GET /system/department/{id}` |
| approval-service | expense-service | OpenFeign | 回调更新业务单据状态 |
| ai-service | expense-service | OpenFeign | 查询发票和报销单数据 |
| notification-service | system-service | OpenFeign | 查询用户信息 |
| expense-service | notification-service | RabbitMQ | `expense.result.notified` |
| ai-service | notification-service | RabbitMQ | `ai.review.completed` |
