# 前端补齐 + 预算管理 + 风控增强 设计文档

> 2026-05-18 | ExpenseFlow 全栈完整性补齐

## 背景

审计结论：后端做了 80%（18+ Controller / 25 张表 / 57 条权限码），前端只暴露了 50%（11 个 Vue 页面）。
核心问题：**后端丰满、前端骨感**。

## 建设范围

### 第一批：系统管理 CRUD（后端 API 已完整，纯前端）
- 用户管理 — `system:user`，UserController 6 个接口
- 角色管理 — `system:role`，RoleController 7 个接口（含权限分配）
- 部门管理 — `system:dept`，DepartmentController 树形接口 + EmployeeController
- 差旅标准 — `policy:*`，ExpensePolicyController 4 个接口

### 第二批：辅助管理 + 审计
- 租户管理 — `system:tenant`，TenantController 6 个接口
- 打款管理 — `finance:payment`，PaymentRecordController
- 审计日志 + AI 审单历史查看

### 第三批：新后端 + 风控
- 部门预算管理 — 新建 sys_department_budget 表 + BudgetController + 打款扣减逻辑
- 风控告警增强 — Drools 规则结果持久化 + 前端展示 + Dashboard 告警卡片

## 实现策略

**模板优先方案**：先做 2 个通用 composable + 3 个通用组件，所有管理页面基于模板快速构建。

### 核心抽象

```
composables/
├── useManagementTable.ts    # 表格页逻辑（分页/搜索/删除/多选）
└── useManagementForm.ts     # 表单弹窗逻辑（新建/编辑/校验/提交）

components/
├── ManagementTable.vue      # 通用表格（title + toolbar + table + pagination）
├── ManagementFormDialog.vue # 通用表单弹窗（dialog + form + 动态字段）
├── SummaryCardRow.vue       # 统计卡片行（表格页顶部，4列以内）
├── StepFormCard.vue         # 步骤式表单容器
└── EmptyState.vue           # 统一空状态插图
```

### 页面开发量对比

| | 每个页面手写 | 用模板 |
|---|---|---|
| 标准 CRUD 页 | ~200 行 | ~60 行 |
| 树形页（部门） | ~250 行 | ~80 行 |
| 带权限分配（角色） | ~300 行 | ~120 行 |

## UI/UX 升级（B 方案）

1. **表格页顶部统计卡片** — `SummaryCardRow`，传 `cards: [{label, value, icon, color}]`
2. **统一空状态** — `<el-empty>` + 引导操作按钮
3. **表格操作列** — 统一用 `link` 类型按钮（减重视觉重量）
4. **统一消息反馈** — `ElMessage.success/error` + `ElMessageBox.confirm`

## 路由与导航

### 侧边栏新增

```
⚙️ 系统管理  (新)              ← SUPER_ADMIN / TENANT_ADMIN 可见
├── 用户管理    /system/users
├── 角色管理    /system/roles
├── 部门管理    /system/departments
├── 租户管理    /system/tenants
└── 差旅标准    /system/policies

💰 财务管理  (新)              ← FINANCE / SUPER_ADMIN 可见
├── 打款管理    /finance/payments
└── 预算管理    /finance/budgets

🤖 智能服务  (现有)
└── 审单日志    /ai/audit-logs  ← 新增
```

## 部门预算管理（新建）

### 数据表

```sql
CREATE TABLE sys_department_budget (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id   BIGINT NOT NULL,
    budget_year     INT NOT NULL,
    budget_quarter  TINYINT COMMENT '1-4, NULL=年度',
    total_amount    DECIMAL(12,2) NOT NULL,
    used_amount     DECIMAL(12,2) DEFAULT 0.00,
    alert_threshold DECIMAL(5,2) DEFAULT 0.80,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    remark          VARCHAR(255),
    created_by      BIGINT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    tenant_id       BIGINT NOT NULL,
    INDEX idx_dept_year (department_id, budget_year, tenant_id)
);
```

### API（BudgetController）

```
GET    /budget/page       分页查询
GET    /budget/{id}       详情（含使用率）
POST   /budget            创建
PUT    /budget/{id}       更新
DELETE /budget/{id}       删除
```

### 打款扣减逻辑

`PaymentRecordController.pay()` 增加事务：
1. 查部门当前预算
2. `used_amount + 本次金额 <= total_amount` 检查
3. 不足 → 拒绝打款 + 钉钉告警
4. 充足 → `used_amount += 本次金额`
5. 使用率 >= `alert_threshold` → 预警通知

## 风控告警增强

### 后端改动

1. `ex_approval_record` 加 `rule_results JSON` 字段，存 Drools 执行结果
2. 新增接口：
   - `GET /approval/tasks/{taskId}/rule-results`
   - `GET /approval/records/{businessId}/rule-logs`

### 前端改动

1. **AIReviewView** — 审单结果面板增加"规则检查明细"列表
2. **Dashboard** — 增加"近期风控告警"卡片（最近 5 条 WARN/BLOCK）

## 种子数据

### 已有用户（7 人）
admin / manager / director / finance / cashier / tenant_admin / employee
密码统一 `admin123`

### 已有角色（6 个）
SUPER_ADMIN / TENANT_ADMIN / EMPLOYEE / APPROVER / FINANCE / CASHIER

### 已有差旅标准（4 条）
交通上限 5000 / 住宿一线 500 / 住宿二线 350 / 餐费 100

### 已有 Drools 规则（11 条）
- 金额阈值 3 条（Travel>5000 need director / Report>10000 warning / Report>20000 extra review）
- 类型合规 3 条（Unrecognized type BLOCK / Exceed max BLOCK / Daily limit WARN）
- 重复检测 2 条（Duplicate invoice BLOCK / Amount-date-vendor match WARN）
- 异常检测 3 条（3x historical avg WARN / 5+ reports/30d WARN / Suspected split WARN）

## 文件清单

```
新增前端文件：
  expense-web/src/composables/useManagementTable.ts
  expense-web/src/composables/useManagementForm.ts
  expense-web/src/components/ManagementTable.vue
  expense-web/src/components/ManagementFormDialog.vue
  expense-web/src/components/SummaryCardRow.vue
  expense-web/src/components/StepFormCard.vue
  expense-web/src/components/EmptyState.vue
  expense-web/src/api/user.ts
  expense-web/src/api/role.ts
  expense-web/src/api/department.ts
  expense-web/src/api/policy.ts
  expense-web/src/api/tenant.ts
  expense-web/src/api/payment.ts
  expense-web/src/api/budget.ts
  expense-web/src/views/system/UserManagement.vue
  expense-web/src/views/system/RoleManagement.vue
  expense-web/src/views/system/DepartmentManagement.vue
  expense-web/src/views/system/TenantManagement.vue
  expense-web/src/views/system/PolicyManagement.vue
  expense-web/src/views/finance/PaymentManagement.vue
  expense-web/src/views/finance/BudgetManagement.vue
  expense-web/src/views/ai/AuditLogViewer.vue

修改前端文件：
  expense-web/src/router/index.ts            # +8 路由
  expense-web/src/layouts/MainLayout.vue     # +2 菜单组
  expense-web/src/views/dashboard/DashboardView.vue  # +风控告警卡片
  expense-web/src/views/ai/AIReviewView.vue  # +规则检查明细

新增后端文件（第三批）：
  expense-service/.../entity/ExDepartmentBudget.java
  expense-service/.../mapper/ExDepartmentBudgetMapper.java
  expense-service/.../service/ExDepartmentBudgetService.java
  expense-service/.../controller/BudgetController.java
  expense-service/.../dto/BudgetDTO.java
  expense-service/.../vo/BudgetVO.java

修改后端文件（第三批）：
  expense-service/.../service/impl/PaymentRecordServiceImpl.java  # +预算扣减
  approval-service/.../controller/ApprovalTaskController.java     # +规则查询接口
  sql/init.sql                                                     # +预算表DDL
```

## 风险与假设

1. 假设后端现有 API 接口与前端需求一致，无需修改后端
2. 假设 Element Plus 版本保持不变，不引入新组件库
3. 部门预算为新建模块，与现有打款流程耦合点需仔细测试
4. Drools 规则结果 JSON 格式需与前端约定好结构
