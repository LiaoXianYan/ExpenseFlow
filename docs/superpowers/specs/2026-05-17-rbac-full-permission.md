# RBAC 完整权限体系设计

**日期**: 2026-05-17  
**状态**: 已确认  
**范围**: 全栈 — 数据库 + 后端 6 服务 + 前端 Vue 3  
**基于**: [ExpenseFlow 架构](../architecture.md) · [M2 网关/系统服务](2026-05-13-m2-gateway-system.md)

## 目标

从当前"后端 50% 接口无授权 + 前端侧边栏无角色过滤"的半成品状态，升级为**完整的 RBAC 权限码体系**，覆盖 6 角色 × 53 权限码 × 15 个 Controller × 前端 3 层控制。

---

## 第一节：权限码体系（53 个）

### 三层结构

| 层级 | type | 说明 | 示例 |
|---|---|---|---|
| 菜单 | 1 | 侧边栏可见性 | `travel`, `approval`, `finance:payment` |
| 操作 | 2 | 页面内按钮显隐 | `travel:create`, `approval:approve` |
| API | 3 | 后端 `@PreAuthorize` 引用 | 复用操作权限码，如 `hasAuthority('invoice:upload')` |

### 完整清单

#### 菜单权限（13）

```
dashboard          工作台
travel             差旅出行
report             费用报销
invoice            票据管理
approval           审批中心
ai:review          智能审单
ai:assistant       政策问答
notification       消息中心
system:user        用户管理
system:role        角色管理
system:tenant      租户管理
finance:payment    打款管理
finance:policy     费用政策
```

#### 操作权限（40）

```
dashboard:view     查看工作台

travel:create      新建出差申请
travel:view        查看出差申请列表
travel:edit        编辑出差申请
travel:delete      删除出差申请

report:create      创建报销单
report:view        查看报销单列表
report:edit        编辑报销单
report:delete      删除报销单
report:submit      提交报销单（流转至审批）
report:withdraw    撤回报销单

invoice:upload     上传发票
invoice:view       查看发票列表
invoice:delete     删除发票

ocr:recognize      触发 OCR 识别
ocr:result         查看 OCR 识别结果

approval:view      查看审批待办列表
approval:approve   审批通过
approval:reject    审批驳回
approval:delegate  委派审批任务

payment:create     发起打款
payment:confirm    确认打款
payment:view       查看打款记录

policy:create      创建费用政策
policy:edit        编辑费用政策
policy:delete      删除费用政策
policy:view        查看费用政策

user:create        创建用户
user:edit          编辑用户
user:delete        删除用户
user:view          查看用户列表
user:assignRole    为用户分配角色

role:create        创建角色
role:edit          编辑角色
role:view          查看角色列表
role:assignPerm    为角色分配权限

tenant:create      创建租户
tenant:edit        编辑租户
tenant:view        查看租户列表

notification:manage    管理通知模板
notification:send      手动发送通知

ai:review:execute  执行 AI 审单
ai:review:result   查看 AI 审单结果
ai:rag:query       RAG 政策问答
```

---

## 第二节：角色-权限映射矩阵

### 菜单可见性

| 菜单权限 | SUPER_ADMIN | TENANT_ADMIN | EMPLOYEE | APPROVER | FINANCE | CASHIER |
|---|---|---|---|---|---|---|
| `dashboard` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `travel` | ✅ | ✅ | ✅ | — | — | — |
| `report` | ✅ | ✅ | ✅ | — | — | — |
| `invoice` | ✅ | ✅ | ✅ | — | — | — |
| `approval` | ✅ | ✅ | — | ✅ | ✅ | — |
| `ai:review` | ✅ | ✅ | — | — | ✅ | — |
| `ai:assistant` | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| `notification` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `system:user` | ✅ | ✅ | — | — | — | — |
| `system:role` | ✅ | ✅ | — | — | — | — |
| `system:tenant` | ✅ | — | — | — | — | — |
| `finance:payment` | ✅ | — | — | — | — | ✅ |
| `finance:policy` | ✅ | — | — | — | ✅ | — |

### 操作权限

| 操作权限 | SUPER_ADMIN | TENANT_ADMIN | EMPLOYEE | APPROVER | FINANCE | CASHIER |
|---|---|---|---|---|---|---|
| `dashboard:view` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `travel:create` | ✅ | ✅ | ✅ | — | — | — |
| `travel:view` | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| `travel:edit` | ✅ | ✅ | ✅ | — | — | — |
| `travel:delete` | ✅ | ✅ | ✅ | — | — | — |
| `report:create` | ✅ | ✅ | ✅ | — | — | — |
| `report:view` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `report:edit` | ✅ | ✅ | ✅ | — | — | — |
| `report:delete` | ✅ | ✅ | ✅ | — | — | — |
| `report:submit` | ✅ | ✅ | ✅ | — | — | — |
| `report:withdraw` | ✅ | ✅ | ✅ | — | — | — |
| `invoice:upload` | ✅ | ✅ | ✅ | — | — | — |
| `invoice:view` | ✅ | ✅ | ✅ | — | ✅ | — |
| `invoice:delete` | ✅ | ✅ | ✅ | — | — | — |
| `ocr:recognize` | ✅ | ✅ | ✅ | — | ✅ | — |
| `ocr:result` | ✅ | ✅ | ✅ | — | ✅ | — |
| `approval:view` | ✅ | ✅ | — | ✅ | ✅ | — |
| `approval:approve` | ✅ | ✅ | — | ✅ | ✅ | — |
| `approval:reject` | ✅ | ✅ | — | ✅ | ✅ | — |
| `approval:delegate` | ✅ | ✅ | — | ✅ | — | — |
| `payment:create` | ✅ | — | — | — | — | ✅ |
| `payment:confirm` | ✅ | — | — | — | — | ✅ |
| `payment:view` | ✅ | — | — | — | ✅ | ✅ |
| `policy:create` | ✅ | — | — | — | ✅ | — |
| `policy:edit` | ✅ | — | — | — | ✅ | — |
| `policy:delete` | ✅ | — | — | — | ✅ | — |
| `policy:view` | ✅ | ✅ | ✅ | ✅ | ✅ | — |
| `user:create` | ✅ | ✅ | — | — | — | — |
| `user:edit` | ✅ | ✅ | — | — | — | — |
| `user:delete` | ✅ | ✅ | — | — | — | — |
| `user:view` | ✅ | ✅ | — | — | — | — |
| `user:assignRole` | ✅ | ✅ | — | — | — | — |
| `role:create` | ✅ | ✅ | — | — | — | — |
| `role:edit` | ✅ | ✅ | — | — | — | — |
| `role:view` | ✅ | ✅ | — | — | — | — |
| `role:assignPerm` | ✅ | ✅ | — | — | — | — |
| `tenant:create` | ✅ | — | — | — | — | — |
| `tenant:edit` | ✅ | — | — | — | — | — |
| `tenant:view` | ✅ | ✅ | — | — | — | — |
| `notification:manage` | ✅ | ✅ | — | — | — | — |
| `notification:send` | ✅ | ✅ | — | — | — | — |
| `ai:review:execute` | ✅ | ✅ | — | — | ✅ | — |
| `ai:review:result` | ✅ | ✅ | — | ✅ | ✅ | — |
| `ai:rag:query` | ✅ | ✅ | ✅ | ✅ | ✅ | — |

### 关键决策

**1. 多角色叠加** — 一个用户可有多个角色（如部门经理 = EMPLOYEE + APPROVER），权限取并集。

**2. 权责分离** — FINANCE 审单据不碰钱，CASHIER 管打款不审单。

**3. 数据范围独立** — `data:own` / `data:dept` 不进入权限码体系，通过 MyBatis-Plus 数据拦截器按 `user_id` 或 `dept_id` 过滤。

---

## 第三节：后端 @PreAuthorize 改造

### 改造总览

| 服务 | Controller | 当前 | 改为 |
|---|---|---|---|
| **system** | `UserController` | `SUPER_ADMIN/TENANT_ADMIN` | `user:view/create/edit/delete/assignRole` |
| | `TenantController` | `SUPER_ADMIN` | `tenant:view/create/edit` |
| | `RoleController` | `SUPER_ADMIN` | `role:view/create/edit/assignPerm` |
| | `PermissionController` | 无限制 | `role:assignPerm` |
| **expense** | `TravelRequestController` | 无限制 | `travel:view/create/edit/delete` |
| | `ExpenseReportController` | 无限制 | `report:view/create/edit/delete/submit/withdraw` |
| | `InvoiceController` | 无限制 | `invoice:upload/view/delete` + `ocr:recognize` |
| | `PaymentRecordController` | `FINANCE/CASHIER/SUPER_ADMIN` | `payment:view/create/confirm` |
| | `ExpensePolicyController` | `SUPER_ADMIN/FINANCE` | `policy:view/create/edit/delete` |
| | `ExpenseItemController` | 无限制 | `report:edit` |
| **approval** | `ApprovalTaskController` | `APPROVER/FINANCE/SUPER_ADMIN` | `approval:view/approve/reject/delegate` |
| **ai** | `OcrController` | 无限制 | `ocr:recognize/result` |
| | `ReviewController` | 无限制 | `ai:review:execute/result` |
| | `RagController` | 无限制 | `ai:rag:query` |
| **notification** | `TemplateController` | `SUPER_ADMIN` | `notification:manage/send` |

### 改造原则

- `hasAnyRole()` → `hasAuthority()`
- 每个 `@PreAuthorize` 引用具体操作权限码，不使用通配
- 查询类接口用 `:view`，写类接口用 `:create/:edit/:delete/:submit` 等
- 新增全局 `AccessDeniedException` 处理器，统一返回 403 JSON

### InvoiceController 完整示例

```java
@RestController
@RequestMapping("/expense/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PreAuthorize("hasAuthority('invoice:upload')")
    @PostMapping("/upload")
    public Result<InvoiceVO> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "ELECTRONIC") String invoiceType) {
        return invoiceService.upload(file, invoiceType);
    }

    @PreAuthorize("hasAuthority('invoice:view')")
    @GetMapping("/page")
    public Result<Page<InvoiceVO>> page(...) { ... }

    @PreAuthorize("hasAuthority('invoice:view')")
    @GetMapping("/{id}")
    public Result<InvoiceVO> getById(@PathVariable Long id) { ... }

    @PreAuthorize("hasAuthority('invoice:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { ... }

    @PreAuthorize("hasAuthority('ocr:recognize')")
    @PostMapping("/{id}/ocr")
    public Result<InvoiceVO> triggerOcr(@PathVariable Long id) { ... }
}
```

### 配套改动

1. **JwtAuthenticationFilter** — 确认 `roles` 列正确映射为 `SimpleGrantedAuthority`
2. **全局 403 处理器** — 统一 JSON 响应格式
3. **改造顺序**: system → expense → approval → ai → notification

---

## 第四节：前端改造

### 架构：拉取权限 → Pinia Store → 3 层消费

```
LoginView.vue  登录成功
  ↓
userStore.login() → 存储 token
  ↓
permissionStore.fetchPermissions() → GET /system/permission/my
  ↓
返回 ["dashboard:view","travel:create","invoice:upload",...]
  ↓
存入 Pinia reactive state
  ↓
┌─────────────────┬──────────────────┬─────────────────┐
│ MainLayout.vue   │ router/index.ts   │ *.vue 页面       │
│ v-if="perm.has() │ meta.permission   │ v-permission     │
│ 菜单按角色显隐    │ 路由按权限码守卫   │ 按钮按权限码移除  │
└─────────────────┴──────────────────┴─────────────────┘
```

### 新增后端接口

```java
// GET /system/permission/my
// 返回当前用户所有权限码（角色叠加取并集）
// 响应: ["dashboard:view","travel:create",...]
```

### 新增文件

| 文件 | 职责 |
|---|---|
| `stores/permission.ts` | Pinia store — `fetchPermissions()`, `has(code)`, `hasAny(codes)`, `reset()` |
| `directives/permission.ts` | `v-permission` 指令 — 无权限时 `el.remove()` |

### 侧边栏改造示例（MainLayout.vue）

```vue
<!-- 差旅出行：仅 EMPLOYEE/SUPER_ADMIN/TENANT_ADMIN -->
<el-sub-menu v-if="perm.has('travel')" index="travel">
  <template #title><el-icon><Promotion /></el-icon><span>差旅出行</span></template>
  <el-menu-item v-if="perm.has('travel:view')" index="/travel">我的行程</el-menu-item>
  <el-menu-item v-if="perm.has('travel:create')" index="/travel/create">新建出差</el-menu-item>
</el-sub-menu>

<!-- 审批中心：仅 APPROVER/FINANCE/SUPER_ADMIN/TENANT_ADMIN -->
<el-menu-item v-if="perm.has('approval')" index="/approval">
  <el-icon><Checked /></el-icon><span>审批中心</span>
</el-menu-item>

<!-- 打款管理：仅 CASHIER/SUPER_ADMIN -->
<el-menu-item v-if="perm.has('finance:payment')" index="/payment">
  <el-icon><Money /></el-icon><span>打款管理</span>
</el-menu-item>
```

### 路由守卫改造

```typescript
// 改前
meta: { roles: ['APPROVER', 'FINANCE', 'SUPER_ADMIN'] }

// 改后
meta: { permission: 'approval' }
```

### 按钮级控制

```html
<el-button v-permission="'report:create'" type="primary">提交报销</el-button>
<el-button v-permission="'approval:approve'" type="success">通过</el-button>
<el-button v-permission="'payment:confirm'" type="warning">确认打款</el-button>
```

### 登录/退出流程

- **登录**: `userStore.login()` → `permissionStore.fetchPermissions()` → `router.push('/dashboard')`
- **退出**: `permissionStore.reset()` + `userStore.logout()` → 清空权限列表

---

## 第五节：测试发票构造

### Mock 模式（当前默认）

`OcrConfig.mock = true` — 上传任意图片均返回固定 Mock 结果：
- 发票号: `MOCK-INV-{invoiceId}`
- 金额: `¥100.00`
- 日期: 当天
- 卖方: "模拟销售方"

**你的 `WDFIPG/` 目录有 13 张 PNG 文件，直接用于上传测试即可。**

### 切换到真实 OCR

```yaml
# application.yml
expense:
  ocr:
    mock: false
    app-code: ${ALIYUN_OCR_APPCODE}
```

### 数据隔离补充

```sql
ALTER TABLE ai_ocr_result ADD COLUMN user_id BIGINT COMMENT '上传用户ID';
```

---

## 实现优先级

| 优先级 | 模块 | 工时估算 |
|---|---|---|
| P0 | SQL 种子数据（53 个权限码 + 角色关联） | 0.5d |
| P0 | PermissionController `/my` 接口 | 0.5d |
| P0 | expense-service 5 个 Controller @PreAuthorize | 1d |
| P1 | approval-service + ai-service Controller | 0.5d |
| P1 | system-service + notification-service Controller | 0.5d |
| P1 | 前端 permission store + directive | 0.5d |
| P1 | MainLayout 侧边栏过滤 + router 守卫 | 1d |
| P2 | 各页面按钮 v-permission | 1d |
| P2 | 全局 403 处理器 + 联调验证 | 0.5d |
| **合计** | | **~6d** |
