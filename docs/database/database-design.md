# ExpenseFlow 数据库设计文档

> 版本：v1.0 | 更新日期：2026-05-14 | 数据库版本：MySQL 8.0+

---

## 1. 数据库概述

### 1.1 基础信息

| 项目 | 说明 |
|------|------|
| 数据库名称 | `expense_flow` |
| 字符集 | `utf8mb4` |
| 排序规则 | `utf8mb4_unicode_ci` |
| 存储引擎 | InnoDB（全部表） |
| 数据库版本 | MySQL 8.0+ |
| 表总数 | 25张业务表（不含Flowable自动生成的60+工作流引擎表） |

### 1.2 设计原则

1. **共享数据库 + tenant_id 隔离**：所有租户共享同一数据库实例，通过每条记录的 `tenant_id` 字段实现多租户数据隔离。
2. **逻辑删除**：绝大多数表使用 `deleted` 字段（0未删除/1已删除），配合 MyBatis-Plus 逻辑删除插件自动过滤。
3. **审计字段**：核心表均包含 `create_time`、`update_time` 字段，用于追踪数据变更时间。
4. **表前缀规范**：按微服务模块划分——`sys_`（系统服务）、`ex_`（差旅报销）、`ap_`（审批引擎）、`ai_`（AI服务）、`nt_`（通知服务）。
5. **业务编号规范化**：关键业务单据使用格式化编号（如 `TR-YYYYMMDD-XXXX`），通过唯一索引保证幂等。

### 1.3 多租户隔离策略

```
┌──────────────────────────────────────────────────┐
│  expense_flow（共享数据库）                       │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ 租户 0   │  │ 租户 1   │  │ 租户 N   │       │
│  │ SYSTEM   │  │  DEMO    │  │  ...     │       │
│  └──────────┘  └──────────┘  └──────────┘       │
│                                                  │
│  隔离方式：每条记录带 tenant_id 字段              │
│  查询时：MyBatis-Plus TenantLineHandler 自动注入  │
│  跨租户：通过 SUPER_ADMIN 角色实现管理级访问      │
└──────────────────────────────────────────────────┘
```

- **数据隔离级别**：行级隔离（Row-Level Security）
- **实现在**：`TenantLineHandler` 从请求头 `X-Tenant-Id` 提取 tenant_id，MyBatis-Plus 分页插件自动为 SQL 追加 `WHERE tenant_id = ?`
- **系统级表**：`sys_permission`（权限表）不含 tenant_id，为全局共享（所有租户使用同一套权限定义）
- **系统默认模板**：`nt_notification_template` 中 `tenant_id=0` 的记录为系统默认模板，所有租户可见
- **种子数据**：内置两个租户——tenant_id=0（SYSTEM 系统默认租户）、tenant_id=1（DEMO 演示租户）

---

## 2. E-R 图（实体关系）

### 2.1 系统服务 (sys_*) — 12张表

```
sys_tenant (租户)
    │
    ├── sys_user (用户) ──────┐
    │       │                 │
    │       ├── sys_user_role (用户角色关联)
    │       │       │
    │       │       └── sys_role (角色)
    │       │               │
    │       │               └── sys_role_permission (角色权限关联)
    │       │                       │
    │       │                       └── sys_permission (权限/菜单)
    │       │
    │       ├── sys_employee (员工)
    │       │       │
    │       │       └── sys_department (部门)
    │       │
    │       └── sys_oauth_user (OAuth2关联)
    │
    ├── sys_department (部门)
    │
    ├── sys_dict_type (字典类型)
    │       │
    │       └── sys_dict_data (字典数据)
    │
    └── sys_audit_log (审计日志)
```

### 2.2 差旅报销服务 (ex_*) — 7张表

```
ex_travel_request (出差申请)
    │
    ├── ex_cost_record (消费记录)
    │       │
    │       └── ex_invoice (发票)
    │
    └── ex_expense_report (报销单)
            │
            ├── ex_expense_item (报销明细)
            │       │
            │       └── ex_invoice (发票)
            │
            └── ex_payment_record (打款流水)

ex_expense_policy (费用政策) [独立维度表]
```

### 2.3 审批引擎服务 (ap_*) — 1张业务表

```
ap_approval_record (审批记录)
    │
    └── 关联业务类型 (business_type)：
        ├── TRAVEL_REQUEST → ex_travel_request
        └── EXPENSE_REPORT → ex_expense_report
```

### 2.4 AI 智能服务 (ai_*) — 3张表

```
ai_ocr_result (OCR识别结果)
    │
    └── 关联 ex_invoice (发票)

ai_review_result (AI审单结果)
    │
    └── 关联业务类型 → ex_expense_report / ex_travel_request

ai_confidence_stats (AI置信度统计) [独立统计表]
```

### 2.5 通知服务 (nt_*) — 2张表

```
nt_notification_template (通知模板)
nt_message (站内消息)
    │
    └── 关联业务类型 → 各业务单据
```

### 2.6 跨服务关键关系

```
sys_user (用户)
    ├── ex_travel_request.applicant_id
    ├── ex_expense_report.applicant_id
    ├── ex_cost_record.user_id
    ├── ex_payment_record.operator_id
    ├── ap_approval_record.approver_id
    ├── nt_message.user_id
    └── sys_employee.user_id

ex_invoice (发票)
    ├── ex_expense_item.invoice_id
    ├── ex_cost_record.invoice_id
    └── ai_ocr_result.invoice_id

ex_expense_report (报销单)
    ├── ex_expense_item.report_id
    ├── ex_cost_record.report_id
    ├── ex_payment_record.report_id
    ├── ai_review_result (business_type=EXPENSE_REPORT)
    └── ap_approval_record (business_type=EXPENSE_REPORT)
```

---

## 3. 25张表详细设计

---

### 3.1 系统服务 — 12张表

#### 表1：sys_tenant（租户表）

**用途**：存储多租户主体信息，是整个多租户体系的核心。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_code | VARCHAR(50) | Y | 租户编码，唯一 |
| tenant_name | VARCHAR(100) | Y | 租户名称 |
| contact_name | VARCHAR(50) | N | 联系人 |
| contact_phone | VARCHAR(20) | N | 联系电话 |
| status | TINYINT | Y | 状态：1-启用 0-禁用（默认1） |
| expire_time | DATETIME | N | 过期时间，NULL表示永久有效 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除：0-正常 1-已删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_tenant_code (tenant_code)`

---

#### 表2：sys_user（用户表）

**用途**：存储系统用户信息，密码使用 BCrypt 加密。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户ID |
| username | VARCHAR(50) | Y | 用户名（租户内唯一） |
| password | VARCHAR(255) | Y | BCrypt加密密码 |
| real_name | VARCHAR(50) | Y | 真实姓名 |
| phone | VARCHAR(20) | N | 手机号 |
| email | VARCHAR(100) | N | 邮箱 |
| avatar | VARCHAR(255) | N | 头像URL |
| status | TINYINT | Y | 1-启用 0-禁用 |
| last_login_time | DATETIME | N | 最后登录时间 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_username_tenant (tenant_id, username)` — 同一租户下用户名唯一
- `KEY idx_tenant (tenant_id)` — 按租户查询

**关联**：
- `tenant_id` → `sys_tenant.id`
- 用户-角色：通过 `sys_user_role` 关联

---

#### 表3：sys_role（角色表）

**用途**：RBAC 权限体系中的角色定义，支持系统内置角色与租户自定义角色。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| role_code | VARCHAR(50) | Y | 角色编码（租户内唯一） |
| role_name | VARCHAR(50) | Y | 角色名称 |
| role_type | TINYINT | Y | 1-系统内置 2-租户自定义 |
| status | TINYINT | Y | 1-启用 0-禁用 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_role_code_tenant (tenant_id, role_code)`
- `KEY idx_tenant (tenant_id)`

**种子数据**（6个系统内置角色）：

| role_code | role_name | role_type | 说明 |
|-----------|-----------|:---:|------|
| SUPER_ADMIN | 超级管理员 | 1 | 全局管理权限 |
| TENANT_ADMIN | 租户管理员 | 1 | 租户级别管理 |
| EMPLOYEE | 普通员工 | 1 | 基础角色 |
| APPROVER | 审批人 | 1 | 审批流程节点 |
| FINANCE | 财务审核员 | 1 | 财务审核权限 |
| CASHIER | 出纳 | 1 | 打款操作权限 |

---

#### 表4：sys_permission（权限表）

**用途**：RBAC 权限体系的权限定义，包含菜单、按钮和API三种类型。此表为全局共享表，不含 tenant_id。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| parent_id | BIGINT | N | 上级权限ID（0表示顶级） |
| permission_code | VARCHAR(100) | Y | 权限标识，如 `system:user:create` |
| permission_name | VARCHAR(100) | Y | 权限名称 |
| permission_type | TINYINT | Y | 1-菜单 2-按钮 3-API |
| path | VARCHAR(255) | N | 路由路径或API路径 |
| icon | VARCHAR(100) | N | 菜单图标（仅菜单类型） |
| sort_order | INT | N | 排序号（默认0） |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_perm_code (permission_code)` — 全局唯一

**关联**：
- `parent_id` → `sys_permission.id`（自关联，实现树形菜单）

---

#### 表5：sys_user_role（用户角色关联表）

**用途**：用户与角色的多对多关联。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| user_id | BIGINT | Y | 用户ID |
| role_id | BIGINT | Y | 角色ID |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_role (user_id, role_id)` — 防止重复分配
- `KEY idx_user (user_id)`
- `KEY idx_role (role_id)`

**种子数据**：
- `(user_id=1, role_id=1)` — 超级管理员
- `(user_id=2, role_id=4)` — manager → APPROVER
- `(user_id=3, role_id=4)` — director → APPROVER
- `(user_id=4, role_id=5)` — finance → FINANCE
- `(user_id=5, role_id=6)` — cashier → CASHIER

---

#### 表6：sys_role_permission（角色权限关联表）

**用途**：角色与权限的多对多关联。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| role_id | BIGINT | Y | 角色ID |
| permission_id | BIGINT | Y | 权限ID |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_role_perm (role_id, permission_id)`
- `KEY idx_role (role_id)`
- `KEY idx_perm (permission_id)`

---

#### 表7：sys_department（部门表）

**用途**：组织架构中的部门定义，支持树形结构。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| parent_id | BIGINT | N | 上级部门ID（0表示顶级） |
| dept_name | VARCHAR(50) | Y | 部门名称 |
| dept_code | VARCHAR(50) | N | 部门编码 |
| leader_id | BIGINT | N | 部门负责人（关联sys_user） |
| sort_order | INT | N | 排序号 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_tenant (tenant_id)`
- `KEY idx_parent (parent_id)` — 支持树形查询

---

#### 表8：sys_employee（员工表）

**用途**：用户与部门的隶属关系，附加工号、职位等员工属性。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| user_id | BIGINT | Y | 关联用户（租户内唯一） |
| department_id | BIGINT | Y | 所属部门 |
| employee_no | VARCHAR(50) | N | 工号 |
| position | VARCHAR(50) | N | 职位 |
| hire_date | DATE | N | 入职日期 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_user_tenant (tenant_id, user_id)` — 一个用户在一个租户下只有一个员工身份
- `KEY idx_dept (department_id)`

---

#### 表9：sys_dict_type（字典类型表）

**用途**：系统字典的类型定义，支持租户级和系统级。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | N | 0=系统级 其他=租户级 |
| dict_code | VARCHAR(50) | Y | 字典编码 |
| dict_name | VARCHAR(50) | Y | 字典名称 |
| status | TINYINT | Y | 1-启用 0-禁用 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_dict_code_tenant (tenant_id, dict_code)`

---

#### 表10：sys_dict_data（字典数据表）

**用途**：字典的具体键值数据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户（默认0） |
| dict_type_id | BIGINT | Y | 所属字典类型 |
| dict_label | VARCHAR(100) | Y | 显示标签 |
| dict_value | VARCHAR(100) | Y | 存储值 |
| sort_order | INT | N | 排序号 |
| status | TINYINT | Y | 1-启用 0-禁用 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_dict_type (dict_type_id)`

---

#### 表11：sys_audit_log（操作审计日志表）

**用途**：记录关键操作的审计日志，用于合规和安全追溯。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| user_id | BIGINT | N | 操作用户ID |
| username | VARCHAR(50) | N | 操作用户名（冗余，防止用户删除后无法追溯） |
| operation | VARCHAR(50) | Y | 操作类型：INSERT/UPDATE/DELETE/EXPORT |
| module | VARCHAR(50) | N | 模块名称 |
| target_type | VARCHAR(50) | N | 操作对象类型 |
| target_id | VARCHAR(100) | N | 操作对象ID |
| request_params | TEXT | N | 请求参数（已脱敏） |
| old_value | TEXT | N | 变更前值（JSON） |
| new_value | TEXT | N | 变更后值（JSON） |
| ip | VARCHAR(50) | N | 客户端IP |
| user_agent | VARCHAR(500) | N | 客户端User-Agent |
| duration | BIGINT | N | 执行耗时（毫秒） |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_tenant_time (tenant_id, create_time)` — 按租户+时间查询审计记录
- `KEY idx_user (user_id)`
- `KEY idx_module (module)`

---

#### 表12：sys_oauth_user（OAuth2用户关联表）

**用途**：关联第三方OAuth2账号（钉钉）与本地用户。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| user_id | BIGINT | Y | 关联的本地用户ID |
| provider | VARCHAR(20) | Y | OAuth2提供方：dingtalk |
| open_id | VARCHAR(100) | Y | 第三方OpenID |
| union_id | VARCHAR(100) | N | 第三方UnionID |
| access_token | VARCHAR(500) | N | OAuth2访问令牌 |
| refresh_token | VARCHAR(500) | N | OAuth2刷新令牌 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_provider_openid (provider, open_id)` — 同一平台的OpenID唯一
- `KEY idx_user (user_id)`

---

### 3.2 差旅报销服务 — 7张表

#### 表13：ex_travel_request（出差申请表）

**用途**：差旅全链路起点，记录员工出差申请信息及审批状态。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| request_no | VARCHAR(50) | Y | 业务编号，格式 TR-YYYYMMDD-XXXX |
| applicant_id | BIGINT | Y | 申请人（sys_user.id） |
| department_id | BIGINT | N | 申请部门 |
| travel_purpose | VARCHAR(500) | Y | 出差目的 |
| destination | VARCHAR(200) | Y | 目的地 |
| start_date | DATE | Y | 出差开始日期 |
| end_date | DATE | Y | 出差结束日期 |
| estimated_amount | DECIMAL(12,2) | N | 预估费用（默认0.00） |
| companions | VARCHAR(500) | N | 同行人 |
| remark | VARCHAR(1000) | N | 备注 |
| status | VARCHAR(20) | Y | DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/WITHDRAWN/CHANGED |
| process_instance_id | VARCHAR(64) | N | Flowable流程实例ID |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_request_no (request_no)` — 业务编号唯一
- `KEY idx_tenant_applicant (tenant_id, applicant_id)` — 按租户+申请人查询
- `KEY idx_status (status)` — 按状态筛选
- `KEY idx_process (process_instance_id)` — 按流程实例查询

**状态流转**：
```
DRAFT → SUBMITTED → APPROVING → APPROVED
                 ↘             ↘
              WITHDRAWN     REJECTED
                              ↓
                          CHANGED（修改后重新提交）
```

---

#### 表14：ex_expense_report（报销单表）

**用途**：报销核心单据，关联出差申请，记录报销金额、审批状态和打款信息。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| report_no | VARCHAR(50) | Y | 业务编号，格式 ER-YYYYMMDD-XXXX |
| applicant_id | BIGINT | Y | 申请人 |
| department_id | BIGINT | N | 申请部门 |
| travel_request_id | BIGINT | N | 关联出差申请 |
| total_amount | DECIMAL(12,2) | Y | 报销总额 |
| actual_amount | DECIMAL(12,2) | N | 实报金额 |
| report_date | DATE | Y | 报销日期 |
| remark | VARCHAR(1000) | N | 备注 |
| status | VARCHAR(20) | Y | DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/WITHDRAWN/PAID |
| process_instance_id | VARCHAR(64) | N | Flowable流程实例ID |
| paid_amount | DECIMAL(12,2) | N | 打款金额 |
| paid_time | DATETIME | N | 打款时间 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_report_no (report_no)`
- `KEY idx_tenant_applicant (tenant_id, applicant_id)`
- `KEY idx_travel (travel_request_id)` — 按出差申请查询关联报销单
- `KEY idx_status (status)`
- `KEY idx_process (process_instance_id)`

**状态流转**：
```
DRAFT → SUBMITTED → APPROVING → APPROVED → PAID
                 ↘             ↘
              WITHDRAWN     REJECTED
```

---

#### 表15：ex_expense_item（报销明细项表）

**用途**：报销单的明细行，记录每笔费用的类型、金额及关联发票。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| report_id | BIGINT | Y | 关联报销单 |
| expense_type | VARCHAR(20) | Y | 费用类型：TRANSPORT/HOTEL/MEAL/OTHER |
| expense_date | DATE | Y | 费用发生日期 |
| amount | DECIMAL(10,2) | Y | 金额 |
| description | VARCHAR(500) | N | 费用描述 |
| invoice_id | BIGINT | N | 关联发票ID |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_report (report_id)` — 按报销单查询明细
- `KEY idx_invoice (invoice_id)` — 按发票查询

---

#### 表16：ex_invoice（发票表）

**用途**：发票信息存储，包含OCR识别状态和识别结果。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| invoice_no | VARCHAR(50) | N | 发票号码 |
| invoice_code | VARCHAR(50) | N | 发票代码 |
| invoice_type | VARCHAR(20) | Y | VAT_SPECIAL/VAT_NORMAL/ELECTRONIC |
| invoice_date | DATE | N | 开票日期 |
| amount | DECIMAL(10,2) | N | 不含税金额 |
| tax_amount | DECIMAL(10,2) | N | 税额 |
| total_amount | DECIMAL(10,2) | N | 价税合计 |
| seller_name | VARCHAR(200) | N | 销售方名称 |
| seller_tax_no | VARCHAR(50) | N | 销售方税号 |
| buyer_name | VARCHAR(200) | N | 购买方名称 |
| buyer_tax_no | VARCHAR(50) | N | 购买方税号 |
| image_url | VARCHAR(500) | N | 发票图片URL |
| ocr_status | VARCHAR(20) | Y | PENDING/PROCESSING/SUCCESS/FAILED |
| ocr_raw_result | TEXT | N | OCR原始返回JSON |
| ocr_confidence | DECIMAL(5,4) | N | OCR置信度（0.0000~1.0000） |
| verify_status | VARCHAR(20) | N | PENDING/VERIFIED/INVALID |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_invoice_no_code (invoice_no, invoice_code)` — 发票号码+代码唯一
- `KEY idx_tenant (tenant_id)`
- `KEY idx_ocr_status (ocr_status)` — 按OCR状态查询待处理发票

---

#### 表17：ex_cost_record（消费记录表）

**用途**：员工在出差期间的消费记录，可关联发票、出差申请和报销单。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| user_id | BIGINT | Y | 消费人 |
| cost_date | DATE | Y | 消费日期 |
| cost_type | VARCHAR(20) | Y | 费用类型 |
| amount | DECIMAL(10,2) | Y | 金额 |
| description | VARCHAR(500) | N | 描述 |
| invoice_id | BIGINT | N | 关联发票 |
| travel_request_id | BIGINT | N | 关联出差申请 |
| report_id | BIGINT | N | 关联报销单 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_tenant_user (tenant_id, user_id)`
- `KEY idx_report (report_id)`
- `KEY idx_travel (travel_request_id)`

---

#### 表18：ex_payment_record（打款流水表）

**用途**：记录报销单的财务打款操作流水。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| report_id | BIGINT | Y | 关联报销单 |
| payment_no | VARCHAR(50) | Y | 打款流水号，格式 PY-YYYYMMDD-XXXX |
| payee_name | VARCHAR(50) | Y | 收款人 |
| payee_account | VARCHAR(50) | N | 收款账号（脱敏存储） |
| amount | DECIMAL(12,2) | Y | 打款金额 |
| payment_method | VARCHAR(20) | N | 打款方式（默认BANK_TRANSFER） |
| payment_status | VARCHAR(20) | Y | PENDING/PROCESSING/SUCCESS/FAILED |
| payment_time | DATETIME | N | 打款时间 |
| operator_id | BIGINT | Y | 出纳（sys_user.id） |
| remark | VARCHAR(500) | N | 备注 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_payment_no (payment_no)`
- `KEY idx_report (report_id)`
- `KEY idx_tenant (tenant_id)`

---

#### 表19：ex_expense_policy（费用政策表）

**用途**：费用报销政策配置，用于审批规则和额度校验的参考依据。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| policy_name | VARCHAR(100) | Y | 政策名称 |
| expense_type | VARCHAR(20) | Y | 费用类型 |
| max_amount | DECIMAL(10,2) | Y | 单次报销上限 |
| daily_limit | DECIMAL(10,2) | N | 日限额 |
| city_tier | VARCHAR(10) | N | 适用城市等级：TIER1/TIER2/TIER3 |
| effective_date | DATE | Y | 生效日期 |
| expire_date | DATE | N | 失效日期（NULL表示长期有效） |
| status | TINYINT | Y | 1-启用 0-禁用 |
| remark | VARCHAR(500) | N | 备注 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_tenant_type (tenant_id, expense_type)` — 按租户+费用类型查询政策

**种子数据**：

| policy_name | expense_type | max_amount | daily_limit | city_tier |
|------------|:---:|:---:|:---:|:---:|
| 交通费标准 | TRANSPORT | 5000.00 | - | TIER1 |
| 住宿费标准-一线 | HOTEL | 500.00 | 500.00 | TIER1 |
| 住宿费标准-其他 | HOTEL | 350.00 | 350.00 | TIER2 |
| 餐费补助 | MEAL | 100.00 | 100.00 | TIER1 |

---

### 3.3 审批引擎服务 — 1张业务表

#### 表20：ap_approval_record（审批记录表）

**用途**：业务维度的审批记录双写，用于查询和报表。Flowable引擎内部流转记录由框架60+张表管理，本表用于业务层面快速查询。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| business_type | VARCHAR(20) | Y | 业务类型：TRAVEL_REQUEST/EXPENSE_REPORT |
| business_id | BIGINT | Y | 业务单据ID |
| process_instance_id | VARCHAR(64) | Y | Flowable流程实例ID |
| task_id | VARCHAR(64) | N | Flowable任务ID |
| task_name | VARCHAR(100) | N | 任务名称（审批节点名称） |
| approver_id | BIGINT | Y | 审批人 |
| approver_name | VARCHAR(50) | N | 审批人姓名（冗余） |
| action | VARCHAR(20) | Y | APPROVE/REJECT/DELEGATE/ADD_SIGN/RETURN |
| comment | VARCHAR(1000) | N | 审批意见 |
| action_time | DATETIME | Y | 操作时间 |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_business (business_type, business_id)` — 按业务单据查询审批记录
- `KEY idx_process (process_instance_id)` — 按流程实例查询
- `KEY idx_approver (approver_id)` — 按审批人查询
- `KEY idx_tenant (tenant_id)`

---

### 3.4 AI 智能服务 — 3张表

#### 表21：ai_ocr_result（OCR识别结果表）

**用途**：存储阿里云OCR对发票图片的识别结果，包含结构化解析数据和置信度。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| invoice_id | BIGINT | Y | 关联发票（ex_invoice.id），一对一 |
| request_id | VARCHAR(100) | N | 阿里云OCR请求ID |
| raw_response | TEXT | N | API原始响应JSON |
| parsed_invoice_no | VARCHAR(50) | N | 解析-发票号码 |
| parsed_invoice_code | VARCHAR(50) | N | 解析-发票代码 |
| parsed_amount | DECIMAL(10,2) | N | 解析-金额 |
| parsed_invoice_date | DATE | N | 解析-开票日期 |
| parsed_seller_name | VARCHAR(200) | N | 解析-销售方名称 |
| parsed_seller_tax_no | VARCHAR(50) | N | 解析-销售方税号 |
| parsed_buyer_name | VARCHAR(200) | N | 解析-购买方名称 |
| parsed_buyer_tax_no | VARCHAR(50) | N | 解析-购买方税号 |
| confidence | DECIMAL(5,4) | N | 综合置信度 |
| status | VARCHAR(20) | Y | SUCCESS/FAILED |
| error_message | VARCHAR(500) | N | 失败时的错误信息 |
| process_time_ms | BIGINT | N | OCR耗时（毫秒） |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_invoice (invoice_id)` — 一张发票对应一条OCR结果
- `KEY idx_tenant (tenant_id)`

---

#### 表22：ai_review_result（AI审单结果表）

**用途**：存储DeepSeek大模型对报销单/出差申请的智能审核结果。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| business_type | VARCHAR(20) | Y | EXPENSE_REPORT/TRAVEL_REQUEST |
| business_id | BIGINT | Y | 业务单据ID |
| model | VARCHAR(50) | Y | 调用的模型（默认deepseek-chat） |
| prompt_tokens | INT | N | 提示词Token数（默认0） |
| completion_tokens | INT | N | 生成Token数（默认0） |
| review_result | VARCHAR(20) | Y | APPROVED/REVIEW_NEEDED/REJECTED |
| risk_level | VARCHAR(10) | N | LOW/MEDIUM/HIGH |
| review_opinion | TEXT | N | AI审单意见 |
| risk_reasons | TEXT | N | 风险原因列表（JSON数组） |
| confidence | DECIMAL(5,4) | N | AI置信度 |
| process_time_ms | BIGINT | N | 审单耗时（毫秒） |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_business (business_type, business_id)` — 按业务查询审单结果
- `KEY idx_tenant (tenant_id)`
- `KEY idx_result (review_result)` — 按审单结果筛选

---

#### 表23：ai_confidence_stats（AI置信度统计表）

**用途**：按日期+租户维度汇总AI审单效果统计，用于模型效果监控和校准。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| stat_date | DATE | Y | 统计日期 |
| total_reviews | INT | N | 审单总数 |
| auto_approved | INT | N | 自动通过数 |
| manual_approved | INT | N | 人工通过数 |
| rejected | INT | N | 驳回数 |
| ai_advice_adopted | INT | N | AI建议采纳数 |
| ai_advice_overridden | INT | N | AI建议被推翻数 |
| avg_confidence | DECIMAL(5,4) | N | 平均置信度 |
| avg_process_time_ms | BIGINT | N | 平均处理耗时(ms) |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_tenant_date (tenant_id, stat_date)` — 每个租户每天一条统计

---

### 3.5 通知服务 — 2张表

#### 表24：nt_message（站内消息表）

**用途**：存储发送给用户的站内消息，支持已读/未读状态管理。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 所属租户 |
| user_id | BIGINT | Y | 接收人 |
| message_type | VARCHAR(20) | Y | SYSTEM/APPROVAL/NOTIFICATION |
| title | VARCHAR(200) | Y | 消息标题 |
| content | TEXT | N | 消息内容 |
| business_type | VARCHAR(20) | N | 关联业务类型 |
| business_id | BIGINT | N | 关联业务ID |
| is_read | TINYINT | Y | 0-未读 1-已读 |
| read_time | DATETIME | N | 阅读时间 |
| create_time | DATETIME | Y | 创建时间 |

**索引**：
- `PRIMARY KEY (id)`
- `KEY idx_tenant_user_read (tenant_id, user_id, is_read)` — 查询用户未读消息
- `KEY idx_create_time (create_time)` — 按时间排序

---

#### 表25：nt_notification_template（通知模板表）

**用途**：消息模板管理，支持多通道（钉钉/站内/邮件）和变量占位符。

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:---:|------|
| id | BIGINT | Y | 主键，自增 |
| tenant_id | BIGINT | Y | 0=系统默认模板 其他=租户自定义模板 |
| template_code | VARCHAR(50) | Y | 模板编码 |
| template_name | VARCHAR(100) | Y | 模板名称 |
| channel | VARCHAR(20) | Y | DINGTALK/IN_APP/EMAIL |
| title_template | VARCHAR(200) | Y | 标题模板（支持`{变量}`占位） |
| content_template | TEXT | Y | 内容模板（支持`{变量}`占位） |
| status | TINYINT | Y | 1-启用 0-禁用 |
| create_time | DATETIME | Y | 创建时间 |
| update_time | DATETIME | N | 更新时间 |
| deleted | TINYINT | Y | 逻辑删除 |

**索引**：
- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_template_code (tenant_id, template_code)`

**种子数据（3个系统默认模板）**：

| template_code | channel | 用途 |
|---------------|:---:|------|
| APPROVAL_PENDING | DINGTALK | 审批待办通知 |
| APPROVAL_RESULT | DINGTALK | 审批结果通知 |
| AI_REVIEW_DONE | IN_APP | AI审单完成通知 |

---

## 4. 种子数据说明

### 4.1 租户数据

| id | tenant_code | tenant_name | 说明 |
|:---:|------------|------------|------|
| 0 | SYSTEM | 系统默认租户 | 系统管理专用 |
| 1 | DEMO | 演示租户 | 功能演示 |

### 4.2 用户数据

| id | username | real_name | 角色 | 说明 |
|:---:|----------|-----------|------|------|
| 1 | admin | 超级管理员 | SUPER_ADMIN | 系统管理员 |
| 2 | manager | 张经理 | APPROVER | 部门经理审批 |
| 3 | director | 李总监 | APPROVER | 总监审批 |
| 4 | finance | 王财务 | FINANCE | 财务审核 |
| 5 | cashier | 赵出纳 | CASHIER | 出纳打款 |

默认密码统一为 `admin123`（BCrypt加密存储）。

### 4.3 费用政策演示数据

4条演示政策，涵盖 TRANSPORT、HOTEL（TIER1/TIER2）、MEAL 四种场景，有效期 2026-01-01 ~ 2027-12-31。

---

## 5. 多租户数据隔离策略

### 5.1 隔离架构

```
请求 → Gateway（提取 X-Tenant-Id 请求头）
           ↓
    各微服务（TenantLineHandler 从 ThreadLocal 获取）
           ↓
    MyBatis-Plus 分页插件自动追加 WHERE tenant_id = ?
           ↓
    SQL 执行（仅访问当前租户数据）
```

### 5.2 实现机制

| 层级 | 机制 |
|------|------|
| HTTP请求 | 前端Axios拦截器自动携带 `X-Tenant-Id` 请求头 |
| Gateway | 校验请求头存在性，透传至下游 |
| Feign调用 | Feign拦截器从 RequestContext 提取 tenant_id，自动写入请求头 |
| ORM层 | MyBatis-Plus `TenantLineHandler` 从ThreadLocal获取tenant_id，自动拼接SQL |
| 系统级表 | `sys_permission` 不含 tenant_id，通过 `@InterceptorIgnore(tenantLine = "true")` 跳过隔离 |

### 5.3 跨租户访问

- **SUPER_ADMIN** 角色可通过管理后台切换租户上下文
- 审计日志 `sys_audit_log` 记录每条操作的 tenant_id，用于跨租户安全审计
- 系统默认模板（`nt_notification_template.tenant_id=0`）对所有租户可见
