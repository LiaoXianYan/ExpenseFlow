# P2-1 前端按钮级权限 — 设计文档

> 版本：v1.0 | 日期：2026-05-16 | 状态：已确认，待实施

## 背景

当前仅有页面级权限控制（`router.beforeEach` + `meta.roles`），无法控制页面内按钮的可见性。审批工作台的"通过/驳回"按钮对所有可访问页面的用户可见，不符合安全要求。

## 方案：v-permission 指令 + 角色→权限码映射

- 新建 `utils/permission.ts`：`hasPermission()` 函数 + 角色→权限码静态映射
- `main.ts` 注册全局 `v-permission` 指令
- 指令逻辑：无权限时 `removeChild`（非 `display:none`），避免被开发者工具恢复
- 支持两种传值：角色名 `['FINANCE']` 和权限码 `'expense:payment:create'`

## 角色→权限码映射

```typescript
const ROLE_PERMISSIONS: Record<string, string[]> = {
  SUPER_ADMIN: ['*'],
  FINANCE: ['approval:approve', 'approval:reject', 'expense:report:delete', 'expense:payment:create'],
  APPROVER: ['approval:approve', 'approval:reject'],
  CASHIER: ['expense:payment:create'],
  USER: [],
}
```

与后端 `@PreAuthorize("@pms.hasPermission(...)")` 规则一致。

## 实施范围

| 视图 | 按钮 | 权限标注 |
|---|---|---|
| `ReportListView.vue` | 删除 | `v-permission="['FINANCE','SUPER_ADMIN']"` |
| `TravelListView.vue` | 删除 | `v-permission="['FINANCE','SUPER_ADMIN']"` |
| `ApprovalWorkbench.vue` | 通过 | `v-permission="'approval:approve'"` |
| `ApprovalWorkbench.vue` | 驳回 | `v-permission="'approval:reject'"` |
| `ApprovalWorkbench.vue` | 委派 | `v-permission="['FINANCE','SUPER_ADMIN']"` |

原则：不改功能，只加标签。现有按钮逻辑完全不动。

## 测试

1 个单元测试文件 `permission.test.ts`，5 个测试：
- 角色匹配 → true
- 角色不匹配 → false
- 权限码匹配 → true
- 通配符 `*` → true
- 数组任一满足逻辑

## 验证标准

1. `npm test` 前端测试通过
2. 用不同角色登录，验证按钮显示/隐藏正确
3. 用 USER 角色登录审批页面，通过/驳回按钮不可见
