# P2-1 前端按钮级权限 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `v-permission` 指令，支持角色名和权限码两种传值，标注 5 个视图的关键按钮。

**Architecture:** `utils/permission.ts` 含 `hasPermission()` 函数 + 角色→权限码静态映射，`main.ts` 注册全局 `v-permission` 指令，无权限时 `removeChild`。

**Tech Stack:** Vue 3 Composition API, TypeScript, Vite, Vitest

---

### Task 1: 创建 permission.ts

**Files:**
- Create: `expense-web/src/utils/permission.ts`

- [ ] **Step 1: 创建 permission.ts**

```typescript
import { getUserRoles } from './jwt'

// 角色→权限码映射（与后端 @PreAuthorize 一致）
const ROLE_PERMISSIONS: Record<string, string[]> = {
  SUPER_ADMIN: ['*'],
  FINANCE: ['approval:approve', 'approval:reject', 'expense:report:delete', 'expense:payment:create'],
  APPROVER: ['approval:approve', 'approval:reject'],
  CASHIER: ['expense:payment:create'],
  USER: [],
}

/**
 * 检查当前用户是否拥有指定角色或权限
 * 含 ':' 视为权限码，否则视为角色名
 */
export function hasPermission(required: string | string[]): boolean {
  const roles = getUserRoles()
  const list = Array.isArray(required) ? required : [required]
  return list.some(p => {
    if (p.includes(':')) {
      // 权限码 → 查映射表
      return roles.some(r => 
        ROLE_PERMISSIONS[r]?.includes(p) || ROLE_PERMISSIONS[r]?.includes('*')
      )
    }
    // 角色名 → 直接比较
    return roles.includes(p)
  })
}
```

- [ ] **Step 2: 编译验证**

```bash
cd expense-web && npx vue-tsc --noEmit
```

Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/utils/permission.ts
git commit -m "feat(web): permission.ts — hasPermission + 角色权限码映射"
```

---

### Task 2: 注册 v-permission 全局指令

**Files:**
- Modify: `expense-web/src/main.ts`

- [ ] **Step 1: 在 main.ts 注册全局指令**

FIRST read: `expense-web/src/main.ts`

在 `const app = createApp(App)` 之后、`app.mount('#app')` 之前，添加：

```typescript
import { hasPermission } from './utils/permission'

app.directive('permission', {
  mounted(el, binding) {
    if (!hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
})
```

文件头部添加 import：
```typescript
import { hasPermission } from './utils/permission'
```

- [ ] **Step 2: 编译验证**

```bash
cd expense-web && npx vue-tsc --noEmit
```

Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/main.ts
git commit -m "feat(web): 注册 v-permission 全局指令 — removeChild 无权限元素"
```

---

### Task 3: 标注按钮权限（3 个视图）

**Files:**
- Modify: `expense-web/src/views/approval/ApprovalWorkbench.vue`
- Modify: `expense-web/src/views/report/ReportListView.vue`
- Modify: `expense-web/src/views/travel/TravelListView.vue`

- [ ] **Step 1: ApprovalWorkbench.vue — 通过/驳回/委派按钮**

FIRST read the file to find the button template section.

在"通过"按钮上添加 `v-permission="'approval:approve'"`：
```html
<el-button size="small" type="success" v-permission="'approval:approve'" @click="handleAction(row, 'APPROVE')">通过</el-button>
```

在"驳回"按钮上添加 `v-permission="'approval:reject'"`：
```html
<el-button size="small" type="danger" v-permission="'approval:reject'" @click="handleAction(row, 'REJECT')">驳回</el-button>
```

在"委派"按钮上添加 `v-permission="['FINANCE','SUPER_ADMIN']"`：
```html
<el-button size="small" type="warning" v-permission="['FINANCE','SUPER_ADMIN']" @click="showDelegate(row)">委派</el-button>
```

- [ ] **Step 2: ReportListView.vue — 删除按钮**

FIRST read the file to find the delete button in the table operations column.

查找 `<el-button>` 中包含"删除"或 `type="danger"` 的按钮，添加：
```html
v-permission="['FINANCE','SUPER_ADMIN']"
```

如果删除操作通过 `@click="handleDelete"` 之类实现，确保只在删除按钮上加这个属性。

- [ ] **Step 3: TravelListView.vue — 删除按钮**

同 Step 2，找到删除按钮并添加：
```html
v-permission="['FINANCE','SUPER_ADMIN']"
```

- [ ] **Step 4: 编译验证**

```bash
cd expense-web && npx vue-tsc --noEmit
```

Expected: No errors. 如果有类型错误（v-permission 未声明），在 `env.d.ts` 中添加类型声明。

If needed, add to `expense-web/src/env.d.ts`:
```typescript
declare module 'vue' {
  interface DirectiveBindings {
    'v-permission': string | string[]
  }
}
```

- [ ] **Step 5: Commit**

```bash
git add expense-web/src/views/
git commit -m "feat(web): 3视图按钮加 v-permission 权限标签"
```

---

### Task 4: 安装 vitest + 编写测试

**Files:**
- Modify: `expense-web/package.json`
- Create: `expense-web/src/utils/__tests__/permission.test.ts`

- [ ] **Step 1: 安装 vitest**

```bash
cd expense-web && npm install -D vitest
```

- [ ] **Step 2: 在 package.json 添加 test script**

```json
"scripts": {
  "dev": "vite",
  "build": "vue-tsc && vite build",
  "preview": "vite preview",
  "test": "vitest run"
}
```

- [ ] **Step 3: 创建测试文件**

Create: `expense-web/src/utils/__tests__/permission.test.ts`

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock localStorage
const mockStorage: Record<string, string> = {}

beforeEach(() => {
  mockStorage.token = ''
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => mockStorage[key] || null,
    setItem: (key: string, val: string) => { mockStorage[key] = val },
    removeItem: (key: string) => { delete mockStorage[key] },
  })
})

// 模拟 JWT payload
function setRoles(roles: string[]) {
  const payload = btoa(JSON.stringify({ roles, sub: '1', tenantId: 0, username: 'test', exp: 9999999999 }))
  mockStorage.token = `header.${payload}.sig`
}

// 动态 import 以确保 mock 先生在模块初始化前生效
async function getHasPermission() {
  const mod = await import('../permission')
  return mod.hasPermission
}

describe('hasPermission', () => {
  it('角色匹配时返回 true', async () => {
    setRoles(['FINANCE'])
    const hasPermission = await getHasPermission()
    expect(hasPermission(['FINANCE'])).toBe(true)
  })

  it('角色不匹配时返回 false', async () => {
    setRoles(['USER'])
    const hasPermission = await getHasPermission()
    expect(hasPermission(['FINANCE'])).toBe(false)
  })

  it('权限码匹配时返回 true（FINANCE 有 expense:payment:create）', async () => {
    setRoles(['FINANCE'])
    const hasPermission = await getHasPermission()
    expect(hasPermission('expense:payment:create')).toBe(true)
  })

  it('SUPER_ADMIN 通配符 * 匹配任意权限码', async () => {
    setRoles(['SUPER_ADMIN'])
    const hasPermission = await getHasPermission()
    expect(hasPermission('any:random:permission')).toBe(true)
  })

  it('数组任一角色满足即返回 true', async () => {
    setRoles(['USER'])
    const hasPermission = await getHasPermission()
    expect(hasPermission(['USER', 'FINANCE'])).toBe(true)
  })

  it('USER 角色无 approve 权限', async () => {
    setRoles(['USER'])
    const hasPermission = await getHasPermission()
    expect(hasPermission('approval:approve')).toBe(false)
  })
})
```

注：因为 `permission.ts` 模块加载时即调用 `getUserRoles()`（来自 jwt.ts 读取 localStorage），测试需要 mock localStorage 后再动态 import。

- [ ] **Step 4: 运行测试**

```bash
cd expense-web && npx vitest run
```

Expected: 6 tests PASS

- [ ] **Step 5: Commit**

```bash
git add expense-web/package.json expense-web/package-lock.json expense-web/src/utils/__tests__/
git commit -m "test(web): permission 6 测试 — 角色/权限码/通配符/数组"
```

---

### Task 5: Vite build 验证

- [ ] **Step 1: 生产构建**

```bash
cd expense-web && npx vite build
```

Expected: Build succeeds, no errors

- [ ] **Step 2: Commit（如有变更）**

## 文件变更清单

| 操作 | 文件 |
|:---:|------|
| **C** | `expense-web/src/utils/permission.ts` |
| **C** | `expense-web/src/utils/__tests__/permission.test.ts` |
| **M** | `expense-web/src/main.ts` |
| **M** | `expense-web/src/views/approval/ApprovalWorkbench.vue` |
| **M** | `expense-web/src/views/report/ReportListView.vue` |
| **M** | `expense-web/src/views/travel/TravelListView.vue` |
| **M** | `expense-web/package.json` |
