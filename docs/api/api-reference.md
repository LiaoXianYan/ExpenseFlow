# ExpenseFlow API 接口文档

> 版本：v1.0 | 更新日期：2026-05-14 | 基础URL：`http://localhost:8080`

---

## 目录

1. [通用约定](#1-通用约定)
2. [网关路由概览](#2-网关路由概览)
3. [system-service（系统服务）](#3-system-service系统服务)
4. [expense-service（差旅报销服务）](#4-expense-service差旅报销服务)
5. [approval-service（审批引擎服务）](#5-approval-service审批引擎服务)
6. [ai-service（AI智能服务）](#6-ai-serviceai智能服务)
7. [notification-service（通知服务）](#7-notification-service通知服务)
8. [Feign 内部调用接口](#8-feign-内部调用接口)
9. [RabbitMQ 事件定义](#9-rabbitmq-事件定义)

---

## 1. 通用约定

### 1.1 统一响应格式

所有接口返回以下JSON结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1706000000000,
  "requestId": "uuid-xxxx-xxxx-xxxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 200-成功，4xx-业务异常，5xx-系统异常 |
| message | string | 提示信息 |
| data | any | 响应数据（分页时为 `{ records, total, page, size }`） |
| timestamp | long | 响应时间戳（毫秒） |
| requestId | string | 请求追踪ID（UUID） |

### 1.2 认证方式

- 除 `/system/auth/login`、`/system/oauth/dingtalk/login` 外，所有接口需携带 JWT Token。
- 请求头：`Authorization: Bearer <access_token>`
- Token有效期：Access Token 2小时，Refresh Token 7天。
- 登录后获取 Token，前端存储于 localStorage，通过 Axios 拦截器自动注入。

### 1.3 多租户

- 请求头：`X-Tenant-Id: <tenant_id>`（除登录接口外必传）
- Gateway 自动校验并透传至下游服务。

### 1.4 分页参数

分页查询接口统一使用以下Query参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|:---:|------|
| page | int | 1 | 页码（从1开始） |
| size | int | 10 | 每页条数 |

分页响应格式：

```json
{
  "code": 200,
  "data": {
    "records": [],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

---

## 2. 网关路由概览

所有请求统一通过 Gateway（端口 8080）接入：

| 服务 | 路由前缀 | 转发目标 |
|------|----------|----------|
| system-service | `/system/**` | `lb://system-service:8081` |
| expense-service | `/expense/**` | `lb://expense-service:8082` |
| approval-service | `/approval/**` | `lb://approval-service:8083` |
| ai-service | `/ai/**` | `lb://ai-service:8084` |
| notification-service | `/notification/**` | `lb://notification-service:8085` |

Gateway统一提供：JWT验签、Sentinel限流、CORS跨域、全局异常处理。

---

## 3. system-service（系统服务）

- **服务端口**：8081
- **网关路径前缀**：`/system`
- **核心职责**：租户管理、用户认证与授权（RBAC）、部门与员工管理、字典数据、OAuth2 SSO

### 3.1 AuthController — 认证管理

**Controller路径**：`/system/auth`

#### `POST /system/auth/login` — 用户登录

**Auth**：无需认证

**RequestBody**（`LoginDTO`）：
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response** (`TokenVO`)：
```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "tenantId": 0,
      "username": "admin",
      "realName": "超级管理员",
      "phone": "13800000000",
      "status": 1
    }
  }
}
```

#### `POST /system/auth/logout` — 退出登录

**Auth**：Bearer Token

**Headers**：`Authorization: Bearer <access_token>`（Token加入Redis黑名单）

**Response**：
```json
{ "code": 200, "message": "success", "data": null }
```

#### `POST /system/auth/refresh` — 刷新Token

**Auth**：Bearer Token（支持Access Token或Refresh Token）

**Headers**：`Authorization: Bearer <token>`

**Response**：同登录，返回新的 `TokenVO`。

#### `GET /system/auth/me` — 获取当前用户信息

**Auth**：Bearer Token

**Response**：返回当前登录用户的 `UserVO` 对象。

---

### 3.2 TenantController — 租户管理

**Controller路径**：`/system/tenant`

**Auth**：SUPER_ADMIN 角色

#### `GET /system/tenant/page` — 分页查询租户

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| keyword | string | N | 按租户编码/名称模糊搜索 |

#### `GET /system/tenant/{id}` — 查询租户详情

#### `POST /system/tenant` — 新增租户

**RequestBody**（`TenantDTO`）：
```json
{
  "tenantCode": "ACME",
  "tenantName": "顶点科技",
  "contactName": "张三",
  "contactPhone": "13900001111"
}
```

#### `PUT /system/tenant/{id}` — 编辑租户

#### `DELETE /system/tenant/{id}` — 删除租户（逻辑删除）

#### `PATCH /system/tenant/{id}/status` — 启/禁用租户

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| status | int | Y | 1-启用 0-禁用 |

---

### 3.3 UserController — 用户管理

**Controller路径**：`/system/user`

**Auth**：TENANT_ADMIN / SUPER_ADMIN

#### `GET /system/user/page` — 分页查询用户

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| keyword | string | N | 按用户名/真实姓名模糊搜索 |

#### `GET /system/user/{id}` — 查询用户详情

#### `POST /system/user` — 新增用户

**RequestBody**（`UserDTO`）：
```json
{
  "username": "zhangsan",
  "password": "123456",
  "realName": "张三",
  "phone": "13900001111",
  "email": "zhangsan@example.com"
}
```

**审计日志**：记录 CREATE 操作到 `sys_audit_log`。

#### `PUT /system/user/{id}` — 编辑用户

**审计日志**：记录 UPDATE 操作。

#### `DELETE /system/user/{id}` — 删除用户

**审计日志**：记录 DELETE 操作。

#### `PATCH /system/user/{id}/status` — 启/禁用用户

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| status | int | Y | 1-启用 0-禁用 |

#### `PATCH /system/user/{id}/password` — 重置密码

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| password | string | Y | 新密码（BCrypt加密后存储） |

---

### 3.4 RoleController — 角色管理

**Controller路径**：`/system/role`

**Auth**：TENANT_ADMIN / SUPER_ADMIN

#### `GET /system/role/list` — 查询全部角色列表

无分页，返回当前租户下所有角色。

#### `POST /system/role` — 新增角色

**RequestBody**（`RoleDTO`）：
```json
{
  "roleCode": "DEPT_MANAGER",
  "roleName": "部门主管"
}
```

新角色默认 `role_type=2`（租户自定义）、`status=1`（启用）。

#### `PUT /system/role/{id}` — 编辑角色

#### `DELETE /system/role/{id}` — 删除角色

同时清理该角色的用户关联和权限关联（事务操作）。

#### `POST /system/role/{id}/users` — 分配角色用户

**RequestBody**（`RoleAssignUsersDTO`）：
```json
{
  "userIds": [1, 2, 3]
}
```

先清除该角色现有用户关联，再全量写入新用户列表。

#### `POST /system/role/{id}/permissions` — 分配角色权限

**RequestBody**（`RoleAssignPermsDTO`）：
```json
{
  "permissionIds": [101, 102, 103, 201]
}
```

先清除该角色现有权限关联，再全量写入新权限列表。

---

### 3.5 PermissionController — 权限管理

**Controller路径**：`/system/permission`

**Auth**：TENANT_ADMIN / SUPER_ADMIN

#### `GET /system/permission/tree` — 查询权限树

返回全部权限的树形结构，用于前端菜单渲染和权限分配页面。

**Response** (`List<PermissionTreeVO>`)：
```json
[
  {
    "id": 1,
    "parentId": 0,
    "permissionCode": "system",
    "permissionName": "系统管理",
    "permissionType": 1,
    "path": "/system",
    "icon": "Setting",
    "sortOrder": 1,
    "children": [
      {
        "id": 11,
        "parentId": 1,
        "permissionCode": "system:user:list",
        "permissionName": "用户列表",
        "permissionType": 2,
        "path": null,
        "icon": null,
        "sortOrder": 1,
        "children": []
      }
    ]
  }
]
```

---

### 3.6 DepartmentController — 部门管理

**Controller路径**：`/system/department`

**Auth**：TENANT_ADMIN / SUPER_ADMIN

#### `GET /system/department/tree` — 查询部门树

返回当前租户下的部门树形结构。

#### `POST /system/department` — 新增部门

**RequestBody**（`DepartmentDTO`）：
```json
{
  "parentId": 0,
  "deptName": "技术部",
  "deptCode": "TECH",
  "leaderId": 1,
  "sortOrder": 1
}
```

#### `PUT /system/department/{id}` — 编辑部门

#### `DELETE /system/department/{id}` — 删除部门

---

### 3.7 EmployeeController — 员工管理

**Controller路径**：`/system/employee`

**Auth**：TENANT_ADMIN

#### `GET /system/employee/page` — 分页查询员工

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| departmentId | long | N | 按部门筛选 |

#### `POST /system/employee` — 添加员工

**RequestBody**（`EmployeeDTO`）：
```json
{
  "userId": 2,
  "departmentId": 1,
  "employeeNo": "EMP001",
  "position": "高级工程师",
  "hireDate": "2025-01-01"
}
```

#### `PUT /system/employee/{id}` — 编辑员工

#### `DELETE /system/employee/{id}` — 删除员工

---

### 3.8 DictController — 字典管理

**Controller路径**：`/system/dict`

**Auth**：TENANT_ADMIN

#### `GET /system/dict/type/list` — 查询字典类型列表

#### `POST /system/dict/type` — 新增字典类型

**RequestBody**（`DictTypeDTO`）：
```json
{
  "dictCode": "INVOICE_TYPE",
  "dictName": "发票类型"
}
```

#### `GET /system/dict/data/list` — 查询字典数据列表

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| typeId | long | Y | 字典类型ID |

#### `POST /system/dict/data` — 新增字典数据

**RequestBody**（`DictDataDTO`）：
```json
{
  "dictTypeId": 1,
  "dictLabel": "增值税专用发票",
  "dictValue": "VAT_SPECIAL",
  "sortOrder": 1
}
```

#### `PUT /system/dict/data/{id}` — 编辑字典数据

#### `DELETE /system/dict/data/{id}` — 删除字典数据

---

### 3.9 OAuthController — 第三方登录

**Controller路径**：`/system/oauth`

**Auth**：无需认证（外部回调入口）

#### `POST /system/oauth/dingtalk/login` — 钉钉扫码登录

**RequestBody**（可选）：
```json
{
  "mock": "true",
  "openId": "dingtalk_open_id_xxx"
}
```

- `mock=true` 时生成虚拟钉钉用户，用于本地开发调试。
- 首次登录自动创建本地用户并关联 OAuth 信息，默认分配 `EMPLOYEE` 角色（id=3）。
- 返回 `TokenVO`（同 `/auth/login` 格式）。

---

## 4. expense-service（差旅报销服务）

- **服务端口**：8082
- **网关路径前缀**：`/expense`
- **核心职责**：出差申请、报销单、费用明细、发票管理、消费记录、打款流水、费用政策

> **注意**：涉及当前用户的接口（出差申请、报销单、消费记录）继承 `BaseController`，从 JWT 中自动提取 `userId`。

### 4.1 TravelRequestController — 出差申请

**Controller路径**：`/expense/travel`

**Auth**：EMPLOYEE及以上

#### `GET /expense/travel/page` — 分页查询我的申请

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| status | string | N | 按状态筛选 |

仅返回当前登录用户的出差申请。

#### `GET /expense/travel/{id}` — 查询申请详情

#### `POST /expense/travel` — 创建出差申请

**RequestBody**（`TravelRequestDTO`）：
```json
{
  "departmentId": 1,
  "travelPurpose": "参加华为云技术峰会",
  "destination": "深圳",
  "startDate": "2026-06-01",
  "endDate": "2026-06-03",
  "estimatedAmount": 5000.00,
  "companions": "李四、王五",
  "remark": "已与对方确认行程"
}
```

自动将 `applicantId` 设为当前登录用户，状态初始为 `DRAFT`。

#### `PUT /expense/travel/{id}` — 编辑出差申请

仅 `DRAFT` 和 `REJECTED` 状态可编辑。

#### `DELETE /expense/travel/{id}` — 删除出差申请

#### `POST /expense/travel/{id}/submit` — 提交出差申请

状态从 `DRAFT` → `SUBMITTED`。提交后触发：
1. 调用 `approval-service` 启动 Flowable 审批流程（Feign）
2. 发起成功后状态更新为 `APPROVING`

#### `POST /expense/travel/{id}/withdraw` — 撤回出差申请

状态从 `SUBMITTED`/`APPROVING` → `WITHDRAWN`。

---

### 4.2 ExpenseReportController — 报销单

**Controller路径**：`/expense/report`

**Auth**：EMPLOYEE及以上

#### `GET /expense/report/page` — 分页查询我的报销单

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| status | string | N | 按状态筛选 |

#### `GET /expense/report/{id}` — 查询报销单详情

返回报销单基本信息 + 关联的报销明细列表。

#### `POST /expense/report` — 创建报销单

**RequestBody**（`ExpenseReportDTO`）：
```json
{
  "departmentId": 1,
  "travelRequestId": 1,
  "totalAmount": 3500.00,
  "reportDate": "2026-06-05",
  "remark": "华为云技术峰会差旅报销"
}
```

#### `PUT /expense/report/{id}` — 编辑报销单

#### `DELETE /expense/report/{id}` — 删除报销单

#### `POST /expense/report/{id}/submit` — 提交报销单

提交后执行：
1. 调用 `ApprovalFeignClient.startApproval()` 启动审批流程
2. 状态更新为 `APPROVING`
3. **发送 RabbitMQ 消息**：`expense.exchange` / `expense.report.submitted` → 触发AI自动审单

#### `POST /expense/report/{id}/withdraw` — 撤回报销单

仅 `SUBMITTED`/`APPROVING`/`APPROVED` 状态可撤回。

---

### 4.3 ExpenseItemController — 报销明细

**Controller路径**：`/expense/report/{reportId}/item`

**Auth**：EMPLOYEE及以上

#### `GET /expense/report/{reportId}/item/list` — 查询报销单下明细列表

#### `POST /expense/report/{reportId}/item` — 添加报销明细

**RequestBody**（`ExpenseItemDTO`）：
```json
{
  "expenseType": "HOTEL",
  "expenseDate": "2026-06-01",
  "amount": 900.00,
  "description": "深圳福田希尔顿 2晚",
  "invoiceId": 1
}
```

费用类型枚举：`TRANSPORT`（交通）、`HOTEL`（住宿）、`MEAL`（餐饮）、`OTHER`（其他）。

#### `DELETE /expense/report/{reportId}/item/{itemId}` — 删除报销明细

---

### 4.4 InvoiceController — 发票管理

**Controller路径**：`/expense/invoice`

**Auth**：EMPLOYEE及以上

#### `POST /expense/invoice/upload` — 上传发票

**Content-Type**：`multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| file | MultipartFile | Y | 发票图片（PNG/JPG/PDF，≤10MB） |
| invoiceType | string | N | 发票类型（默认ELECTRONIC） |

上传后自动触发OCR识别流程：
1. 保存图片到文件存储
2. 创建 `ex_invoice` 记录（ocr_status=PENDING）
3. 异步调用 ai-service OCR服务

#### `GET /expense/invoice/page` — 分页查询发票

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |
| ocrStatus | string | N | 按OCR状态筛选 |

#### `GET /expense/invoice/{id}` — 查询发票详情

#### `POST /expense/invoice/{id}/ocr` — 手动触发OCR识别

对指定发票重新发起OCR识别。ai-service 识别完成后通过 Feign 回调 `/expense/callback/ocr-result` 更新发票信息。

---

### 4.5 CostRecordController — 消费记录

**Controller路径**：`/expense/cost`

**Auth**：EMPLOYEE及以上

#### `GET /expense/cost/page` — 分页查询我的消费记录

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |

#### `POST /expense/cost` — 新增消费记录

**RequestBody**（`CostRecordDTO`）：
```json
{
  "costDate": "2026-06-01",
  "costType": "MEAL",
  "amount": 80.00,
  "description": "午餐",
  "invoiceId": null,
  "travelRequestId": 1
}
```

#### `PUT /expense/cost/{id}` — 编辑消费记录

#### `DELETE /expense/cost/{id}` — 删除消费记录

---

### 4.6 PaymentRecordController — 打款管理

**Controller路径**：`/expense/payment`

**Auth**：CASHIER 角色

#### `GET /expense/payment/page` — 分页查询打款流水

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |

#### `POST /expense/payment/pay` — 执行打款

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| reportId | long | Y | 报销单ID |

操作用户自动从JWT获取（operator_id）。执行后：
1. 生成打款流水号 `PY-YYYYMMDD-XXXX`
2. 更新 `ex_payment_record`
3. 更新 `ex_expense_report` 状态为 `PAID`，记录 `paid_amount` + `paid_time`

---

### 4.7 ExpensePolicyController — 费用政策

**Controller路径**：`/expense/policy`

**Auth**：TENANT_ADMIN / FINANCE

#### `GET /expense/policy/list` — 查询政策列表

返回当前租户下全部费用政策。

#### `POST /expense/policy` — 新增费用政策

**RequestBody**（`ExpensePolicyDTO`）：
```json
{
  "policyName": "交通费标准-2026",
  "expenseType": "TRANSPORT",
  "maxAmount": 5000.00,
  "dailyLimit": null,
  "cityTier": "TIER1",
  "effectiveDate": "2026-01-01",
  "remark": "高铁一等座、经济舱标准"
}
```

#### `PUT /expense/policy/{id}` — 编辑费用政策

#### `DELETE /expense/policy/{id}` — 删除费用政策

---

### 4.8 ApprovalCallbackController — 审批回调（内部）

**Controller路径**：`/expense/callback`

**Auth**：Feign内部调用（Gateway放行）

#### `PUT /expense/callback/approval-result` — 接收审批结果回调

**RequestBody**：
```json
{
  "businessType": "EXPENSE_REPORT",
  "businessId": 1,
  "outcome": "APPROVED"
}
```

处理逻辑：
1. 更新业务单据状态（`APPROVED` 或 `REJECTED`）
2. **发送 RabbitMQ 消息**：`expense.exchange` / `expense.result.notified` → 触发通知推送

---

## 5. approval-service（审批引擎服务）

- **服务端口**：8083
- **网关路径前缀**：`/approval`
- **核心职责**：Flowable工作流引擎驱动下的审批流程启动、任务处理、审批记录管理

> **注意**：Flowable 60+ 张工作流引擎表由框架自动创建，不在本文档范围内。本服务仅暴露业务层面的 REST 接口。

### 5.1 ApprovalProcessController — 审批流程

**Controller路径**：`/approval/process`

**Auth**：内部调用（Feign）或 SYSTEM 角色

#### `POST /approval/process/start` — 启动审批流程

**RequestBody**（`ApprovalStartDTO`）：
```json
{
  "businessType": "EXPENSE_REPORT",
  "businessId": 1,
  "applicantId": 2,
  "amount": 3500.00,
  "processDefinitionKey": "expense-approval"
}
```

**Response** (`ProcessStartResponse`)：
```json
{
  "processInstanceId": "uuid-flowable-xxxx",
  "status": "STARTED"
}
```

#### `PUT /approval/process/callback/result` — 审批完成回调

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| businessType | string | Y | TRAVEL_REQUEST/EXPENSE_REPORT |
| businessId | long | Y | 业务单据ID |
| outcome | string | Y | APPROVED/REJECTED |

审批完成后由 Flowable 回调此接口，再通过 Feign 调用 expense-service 的 `/expense/callback/approval-result` 回写状态。

---

### 5.2 ApprovalTaskController — 审批任务

**Controller路径**：`/approval/task`

**Auth**：APPROVER 及以上

#### `GET /approval/task/page` — 查询待办任务

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| candidateGroup | string | N | 候选组名（不传时按当前用户查询） |

两种查询模式：
- 传 `candidateGroup`：按候选组查询（部门领导审批等）
- 不传：按当前登录用户ID查询（即 `assignee`）

#### `POST /approval/task/{taskId}/complete` — 完成任务

**RequestBody**（`TaskCompleteDTO`）：
```json
{
  "action": "APPROVE",
  "comment": "同意报销，费用合理"
}
```

- `action` 值域：`APPROVE`、`REJECT`
- 审批人ID自动从JWT获取。
- 任务完成后 Flowable 自动流转到下一节点；若无下一节点，触发回调更新业务状态。

#### `POST /approval/task/{taskId}/delegate` — 转办任务

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| delegateToUser | string | Y | 转办目标用户标识 |

将当前审批任务转交给其他审批人处理。

#### `GET /approval/task/record/list` — 查询审批记录

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| businessType | string | Y | TRAVEL_REQUEST/EXPENSE_REPORT |
| businessId | long | Y | 业务单据ID |

返回指定业务单据的完整审批链记录。

**Response** (`List<ApprovalRecordVO>`)：
```json
[
  {
    "approverName": "张经理",
    "taskName": "部门经理审批",
    "action": "APPROVE",
    "comment": "同意",
    "actionTime": "2026-06-06 10:30:00"
  },
  {
    "approverName": "李总监",
    "taskName": "总监审批",
    "action": "APPROVE",
    "comment": "通过",
    "actionTime": "2026-06-06 14:00:00"
  }
]
```

---

## 6. ai-service（AI智能服务）

- **服务端口**：8084
- **网关路径前缀**：`/ai`
- **核心职责**：阿里云OCR发票识别、DeepSeek大模型智能审单、RAG智能问答

### 6.1 OcrController — OCR识别

**Controller路径**：`/ai/ocr`

**Auth**：EMPLOYEE及以上

#### `POST /ai/ocr/recognize` — 执行OCR识别

**RequestBody**（`OcrRequestDTO`）：
```json
{
  "invoiceId": 1,
  "imageUrl": "https://oss.example.com/invoices/xxx.jpg"
}
```

调用阿里云OCR API（AppCode认证）识别发票，返回结构化数据。

**Response** (`OcrResultVO`)：
```json
{
  "invoiceId": 1,
  "parsedInvoiceNo": "4401234567",
  "parsedInvoiceCode": "123456789012",
  "parsedAmount": 1000.00,
  "parsedInvoiceDate": "2026-05-01",
  "parsedSellerName": "深圳科技有限公司",
  "parsedBuyerName": "顶点科技有限公司",
  "confidence": 0.9823,
  "status": "SUCCESS"
}
```

tenant_id 自动从 BaseController 的 `getCurrentTenantId()` 获取。

#### `GET /ai/ocr/{id}` — 查询OCR结果

根据 ai_ocr_result 的 id 查询历史识别结果。

---

### 6.2 ReviewController — AI智能审单

**Controller路径**：`/ai/review`

**Auth**：APPROVER / FINANCE

#### `POST /ai/review/evaluate` — AI审单评估

**RequestBody**（`ReviewRequestDTO`）：
```json
{
  "businessType": "EXPENSE_REPORT",
  "businessId": 1,
  "totalAmount": 3500.00
}
```

调用 DeepSeek Chat API 对报销单进行智能审核，返回评估结果。

**Response** (`ReviewResultVO`)：
```json
{
  "reviewResult": "REVIEW_NEEDED",
  "riskLevel": "MEDIUM",
  "reviewOpinion": "住宿费超出标准25%，建议人工复核是否符合特批条件",
  "riskReasons": [
    "住宿费超出TIER1标准(500元/天)：实际600元/天",
    "缺少交通票据明细"
  ],
  "confidence": 0.8750,
  "processTimeMs": 2340
}
```

#### `POST /ai/review/risk` — 风险分析

**RequestBody**（`RiskAnalysisDTO`）：
```json
{
  "reportId": 1
}
```

对指定报销单进行风险分析，结合历史数据和费用政策。

**Response** (`RiskReportVO`)：
```json
{
  "reportId": 1,
  "overallRisk": "MEDIUM",
  "riskItems": [...],
  "suggestions": [...]
}
```

---

### 6.3 RagController — RAG智能问答

**Controller路径**：`/ai/rag`

**Auth**：所有已认证用户

#### `POST /ai/rag/ask` — RAG问答

**RequestBody**（`RagQuestionDTO`）：
```json
{
  "question": "住宿费每天可以报销多少钱？"
}
```

基于 LangChain4j + DeepSeek Chat 实现检索增强生成（RAG），回答差旅政策相关问题。

**Response** (`RagAnswerVO`)：
```json
{
  "question": "住宿费每天可以报销多少钱？",
  "answer": "根据公司差旅政策，一线城市住宿标准为500元/天，其他城市为350元/天。具体标准以您所在租户的费用政策为准。",
  "sources": [
    {"policyName": "住宿费标准-一线", "maxAmount": 500},
    {"policyName": "住宿费标准-其他", "maxAmount": 350}
  ],
  "confidence": 0.92
}
```

---

## 7. notification-service（通知服务）

- **服务端口**：8085
- **网关路径前缀**：`/notification`
- **核心职责**：站内消息管理、通知模板管理、钉钉机器人推送、RabbitMQ消息消费

### 7.1 MessageController — 站内消息

**Controller路径**：`/notification/message`

**Auth**：所有已认证用户

#### `GET /notification/message/page` — 分页查询我的消息

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| page | int | N | 默认1 |
| size | int | N | 默认10 |

自动过滤当前用户的消息，按时间倒序排列。

#### `PUT /notification/message/{id}/read` — 标记单条已读

#### `PUT /notification/message/read-all` — 全部标记已读

一键将当前用户所有未读消息标为已读。

#### `GET /notification/message/unread-count` — 查询未读数量

**Response**：
```json
{ "code": 200, "data": 5 }
```

---

### 7.2 TemplateController — 通知模板

**Controller路径**：`/notification/template`

**Auth**：TENANT_ADMIN

#### `GET /notification/template/list` — 查询模板列表

返回所有模板（含系统默认模板和租户自定义模板）。

#### `GET /notification/template/{code}` — 按编码查询模板

#### `POST /notification/template` — 新增模板

**RequestBody**（`TemplateDTO`）：
```json
{
  "templateCode": "EXPENSE_APPROVED",
  "templateName": "报销通过通知",
  "channel": "DINGTALK",
  "titleTemplate": "【报销通过】您的报销单{单据编号}已审批通过",
  "contentTemplate": "报销金额：{金额}元\n审批人：{审批人}\n审批时间：{审批时间}"
}
```

#### `PUT /notification/template/{id}` — 编辑模板

---

## 8. Feign 内部调用接口

以下接口供微服务间通过 OpenFeign 内部调用，不通过 Gateway 对外暴露。

### 8.1 expense-service → approval-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| POST | `/approval/process/start` | 启动审批流程 | 报销单/出差申请提交时 |

**FeignClient**：`ApprovalFeignClient`（`name=approval-service`）
**熔断**：`ApprovalFeignFallbackFactory` — 失败时返回默认流程实例ID

### 8.2 expense-service → system-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| GET | `/system/user/{id}` | 查询用户 | 获取申请人/审批人信息 |
| GET | `/system/department/{id}` | 查询部门 | 获取部门名称 |

**FeignClient**：`SystemFeignClient`（`name=system-service`）
**熔断**：`SystemFeignFallbackFactory`

### 8.3 approval-service → expense-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| PUT | `/expense/callback/approval-result` | 回写审批结果 | 审批流完成后更新业务单据状态 |

**FeignClient**：`ExpenseFeignClient`（`name=expense-service`）
**熔断**：`ExpenseFeignFallbackFactory`

### 8.4 approval-service → system-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| GET | `/system/user/{id}` | 查询用户 | 获取审批人信息 |
| GET | `/system/department/{id}` | 查询部门 | 获取流程中的部门信息 |

**FeignClient**：`SystemFeignClient`（`name=system-service`）
**熔断**：`SystemFeignFallbackFactory`

### 8.5 ai-service → expense-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| PUT | `/expense/callback/ocr-result` | 回写OCR结果 | OCR识别完成后更新发票信息 |

**参数**：`invoiceId`、`ocrStatus`、`invoiceNo`（可选）、`amount`（可选）
**FeignClient**：`ExpenseFeignClient`（`name=expense-service`）
**熔断**：`ExpenseFeignFallbackFactory`

### 8.6 ai-service → system-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| GET | `/system/user/{id}` | 查询用户 | 获取业务提交人信息 |

**FeignClient**：`SystemFeignClient`（`name=system-service`）
**熔断**：`SystemFeignFallbackFactory`

### 8.7 notification-service → system-service

| 方法 | 路径 | 说明 | 调用场景 |
|------|------|------|----------|
| GET | `/system/user/{id}` | 查询用户 | 获取钉钉通知接收人信息 |

**FeignClient**：`SystemFeignClient`（`name=system-service`）
**熔断**：`SystemFeignFallbackFactory`

---

## 9. RabbitMQ 事件定义

### 9.1 架构总览

```
生产者 (expense-service)
    │
    ├── 报销单提交 ──→ expense.exchange / expense.report.submitted
    │                      ↓
    │                  ai.review.queue (ai-service 消费)
    │                      ↓ AI审单完成
    │                  ai.review.completed → notification.event.queue
    │
    └── 审批结果回调 ──→ expense.exchange / expense.result.notified
                           ↓
                       notification.event.queue (notification-service 消费)
                           ↓
                       站内消息 + 钉钉推送
```

### 9.2 Exchange 定义

| Exchange | 类型 | 说明 |
|----------|:---:|------|
| `expense.exchange` | Topic | 差旅报销领域事件总交换 |

### 9.3 Queue 定义

| Queue | 持久化 | 服务归属 | 说明 |
|-------|:---:|------|------|
| `ai.review.queue` | Durable | ai-service | AI审单任务队列 |
| `notification.event.queue` | Durable | notification-service | 通知事件队列 |

### 9.4 Binding 定义

| Routing Key | 绑定 Queue | 定义位置 |
|-------------|-----------|----------|
| `expense.report.submitted` | `ai.review.queue` | ai-service RabbitMQConfig |
| `expense.result.notified` | `notification.event.queue` | notification-service RabbitMQConfig |
| `ai.review.completed` | `notification.event.queue` | notification-service RabbitMQConfig |

### 9.5 事件详情

#### 事件1：报销单提交 → AI审单

- **发送方**：expense-service（`ExpenseReportServiceImpl.submit()`）
- **Exchange**：`expense.exchange`
- **Routing Key**：`expense.report.submitted`
- **Queue**：`ai.review.queue`
- **消费方**：ai-service `RabbitMQConsumer.onReportSubmitted()`
- **消息体**：

```json
{
  "eventId": "uuid-xxxx",
  "reportId": 1,
  "amount": 3500.00,
  "tenantId": 0
}
```

- **幂等**：基于 `eventId`（UUID）去重处理。

#### 事件2：审批结果 → 通知推送

- **发送方**：expense-service（`ApprovalCallbackController.updateApprovalResult()`）
- **Exchange**：`expense.exchange`
- **Routing Key**：`expense.result.notified`
- **Queue**：`notification.event.queue`
- **消费方**：notification-service `RabbitMQConsumer.onNotificationEvent()`
- **消息体**：

```json
{
  "eventId": "uuid-xxxx",
  "eventType": "approval.result",
  "businessType": "EXPENSE_REPORT",
  "businessId": 1,
  "outcome": "APPROVED",
  "tenantId": 0,
  "applicantId": 2
}
```

- **消费处理**：
  1. 写入 `nt_message` 站内消息
  2. 调用钉钉机器人推送通知

#### 事件3：AI审单完成 → 通知推送（预留）

- **Routing Key**：`ai.review.completed`
- 由 ai-service 在审单完成后发送，notification-service 消费后推送 AI 审单结果通知。

### 9.6 消息序列化

- 所有消息使用 **Jackson2JsonMessageConverter** 序列化为 JSON。
- 各服务的 `RabbitMQConfig` 均声明 `jackson2JsonMessageConverter` Bean。

### 9.7 可靠性保障

| 机制 | 实现 |
|------|------|
| 消息持久化 | Queue声明为 `Durable`（`QueueBuilder.durable()`） |
| 消费确认 | 默认 Auto Ack，异常被 catch 处理以防止无限重试 |
| 幂等消费 | 消息体携带 `eventId`（UUID），消费端基于此字段去重 |
| 发送失败重试 | 异常被 catch 并记录日志，不阻塞主流程（Tolerant Reader模式） |
