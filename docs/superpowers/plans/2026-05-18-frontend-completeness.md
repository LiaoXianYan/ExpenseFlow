# Frontend Completeness + Budget + Risk Control 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 8 个缺失前端管理页面 + 新建部门预算管理 + 打通 Drools 风控告警到前端

**Architecture:** 模板优先 — 先做 `useManagementTable` / `useManagementForm` 两个 composable 和 `ManagementTable` / `ManagementFormDialog` 通用组件，然后所有 CRUD 管理页面基于模板快速构建。特殊页面（部门树、角色权限分配、预算管理）通过 slot 扩展。

**Tech Stack:** Vue 3.4 + TypeScript + Element Plus + 现有后端 API（Spring Boot 3.3）

---

## 文件结构

```
新增文件 (25):
  expense-web/src/composables/useManagementTable.ts
  expense-web/src/composables/useManagementForm.ts
  expense-web/src/components/ManagementTable.vue
  expense-web/src/components/ManagementFormDialog.vue
  expense-web/src/components/SummaryCardRow.vue
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
  expense-service/src/main/java/com/expenseflow/expense/entity/ExDepartmentBudget.java
  expense-service/src/main/java/com/expenseflow/expense/mapper/ExDepartmentBudgetMapper.java
  expense-service/src/main/java/com/expenseflow/expense/service/ExDepartmentBudgetService.java
  expense-service/src/main/java/com/expenseflow/expense/service/impl/ExDepartmentBudgetServiceImpl.java
  expense-service/src/main/java/com/expenseflow/expense/controller/BudgetController.java

修改文件 (7):
  expense-web/src/router/index.ts
  expense-web/src/layouts/MainLayout.vue
  expense-web/src/views/dashboard/DashboardView.vue
  expense-web/src/views/ai/AIReviewView.vue
  expense-service/src/main/java/com/expenseflow/expense/service/impl/PaymentRecordServiceImpl.java
  approval-service/src/main/java/com/expenseflow/approval/controller/ApprovalTaskController.java
  sql/init.sql
```

---

### Task 1: useManagementTable 通用表格 composable

**Files:**
- Create: `expense-web/src/composables/useManagementTable.ts`

- [ ] **Step 1: 创建 useManagementTable composable**

```typescript
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface TableOptions {
  fetchApi: (params: Record<string, any>) => Promise<any>
  deleteApi?: (id: number) => Promise<any>
  defaultPageSize?: number
}

export function useManagementTable(options: TableOptions) {
  const tableData = ref<any[]>([])
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(options.defaultPageSize || 10)
  const searchKeyword = ref('')

  async function loadData() {
    loading.value = true
    try {
      const res = await options.fetchApi({
        page: page.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
      })
      tableData.value = res.data?.records ?? []
      total.value = res.data?.total ?? 0
    } catch {
      tableData.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  function onSearch() {
    page.value = 1
    loadData()
  }

  function onReset() {
    searchKeyword.value = ''
    page.value = 1
    loadData()
  }

  function onPageChange(p: number) {
    page.value = p
    loadData()
  }

  function onSizeChange(s: number) {
    pageSize.value = s
    page.value = 1
    loadData()
  }

  async function handleDelete(id: number, label?: string) {
    if (!options.deleteApi) return
    try {
      await ElMessageBox.confirm(`确认删除${label ?? '该项'}？`, '删除确认', {
        type: 'warning',
      })
    } catch {
      return
    }
    try {
      await options.deleteApi(id)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      // error handled by interceptor
    }
  }

  return {
    tableData, loading, total, page, pageSize, searchKeyword,
    loadData, onSearch, onReset, onPageChange, onSizeChange, handleDelete,
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add expense-web/src/composables/useManagementTable.ts
git commit -m "feat(web): add useManagementTable composable for CRUD pages"
```

---

### Task 2: useManagementForm 通用表单弹窗 composable

**Files:**
- Create: `expense-web/src/composables/useManagementForm.ts`

- [ ] **Step 1: 创建 useManagementForm composable**

```typescript
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

export interface FormOptions<T extends Record<string, any>> {
  createApi?: (data: T) => Promise<any>
  updateApi?: (id: number, data: T) => Promise<any>
  getDetailApi?: (id: number) => Promise<any>
  defaultForm: T
  rules?: FormRules
  onSuccess: () => void
}

export function useManagementForm<T extends Record<string, any>>(options: FormOptions<T>) {
  const dialogVisible = ref(false)
  const dialogTitle = ref('新建')
  const isEdit = ref(false)
  const formData = reactive<T>({ ...options.defaultForm }) as T
  const formRef = ref<FormInstance>()
  const submitting = ref(false)
  let editId: number | null = null

  function handleCreate() {
    dialogTitle.value = '新建'
    isEdit.value = false
    editId = null
    Object.assign(formData, { ...options.defaultForm })
    formRef.value?.resetFields()
    dialogVisible.value = true
  }

  async function handleEdit(id: number) {
    dialogTitle.value = '编辑'
    isEdit.value = true
    editId = id
    if (options.getDetailApi) {
      try {
        const res = await options.getDetailApi(id)
        Object.assign(formData, res.data ?? {})
      } catch {
        return
      }
    }
    formRef.value?.resetFields()
    dialogVisible.value = true
  }

  async function handleSubmit() {
    if (!formRef.value) return
    try {
      await formRef.value.validate()
    } catch {
      return
    }
    submitting.value = true
    try {
      if (isEdit.value && options.updateApi && editId !== null) {
        await options.updateApi(editId, { ...formData })
        ElMessage.success('更新成功')
      } else if (options.createApi) {
        await options.createApi({ ...formData })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      options.onSuccess()
    } catch {
      // error handled by interceptor
    } finally {
      submitting.value = false
    }
  }

  function handleClose() {
    dialogVisible.value = false
  }

  return {
    dialogVisible, dialogTitle, isEdit, formData, formRef, submitting,
    handleCreate, handleEdit, handleSubmit, handleClose,
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add expense-web/src/composables/useManagementForm.ts
git commit -m "feat(web): add useManagementForm composable for CRUD dialogs"
```

---

### Task 3: ManagementTable 通用表格组件

**Files:**
- Create: `expense-web/src/components/ManagementTable.vue`
- Create: `expense-web/src/components/SummaryCardRow.vue`

- [ ] **Step 1: 创建 SummaryCardRow 统计卡片行组件**

```vue
<!-- expense-web/src/components/SummaryCardRow.vue -->
<template>
  <el-row :gutter="16" class="summary-row">
    <el-col v-for="card in cards" :key="card.label" :span="Math.min(6, 24 / cards.length)">
      <div class="summary-card" :style="{ borderTopColor: card.color ?? '#3B82F6' }">
        <div class="summary-value">{{ card.value }}</div>
        <div class="summary-label">{{ card.label }}</div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
defineProps<{
  cards: { label: string; value: string | number; color?: string; icon?: string }[]
}>()
</script>

<style scoped>
.summary-row { margin-bottom: 16px; }
.summary-card {
  background: #fff; border-radius: 12px; padding: 18px 20px;
  border-top: 3px solid #3B82F6;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.summary-value { font-size: 28px; font-weight: 700; color: #1E293B; line-height: 1.2; }
.summary-label { font-size: 13px; color: #94A3B8; margin-top: 4px; }
</style>
```

- [ ] **Step 2: 创建 ManagementTable 通用表格组件**

```vue
<!-- expense-web/src/components/ManagementTable.vue -->
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>{{ title }}</h2>
        <p class="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <slot name="header-actions" />
      </div>
    </div>

    <SummaryCardRow v-if="summaryCards?.length" :cards="summaryCards" />

    <el-card class="table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-if="searchable !== false"
            v-model="searchKeywordModel"
            placeholder="搜索..."
            clearable
            style="width: 240px"
            @keyup.enter="$emit('search')"
            @clear="$emit('search')"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <slot name="toolbar-filters" />
        </div>
        <div class="toolbar-right">
          <slot name="toolbar-actions" />
        </div>
      </div>

      <el-table
        :data="data" v-loading="loading" stripe class="data-table"
        @selection-change="(rows: any[]) => $emit('selectionChange', rows)"
      >
        <el-table-column v-if="selectable" type="selection" width="50" />
        <template v-for="col in columns" :key="col.prop">
          <el-table-column
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
          >
            <template v-if="col.slot" #default="{ row }">
              <slot :name="col.slot" :row="row" />
            </template>
          </el-table-column>
        </template>
        <el-table-column v-if="$slots['row-actions'] || rowActions?.length" label="操作" :width="rowActionsWidth ?? 240" fixed="right">
          <template #default="{ row }">
            <slot name="row-actions" :row="row">
              <template v-for="act in rowActions" :key="act.label">
                <el-button
                  v-if="!act.visible || act.visible(row)"
                  :type="act.type || 'primary'"
                  link
                  size="small"
                  @click="act.onClick(row)"
                >
                  {{ act.label }}
                </el-button>
              </template>
            </slot>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && data.length === 0" description="暂无数据" :image-size="100">
        <slot name="empty-action" />
      </el-empty>

      <div v-if="total > pageSize" class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPageModel"
          :total="total"
          :page-size="pageSize"
          layout="prev,pager,next,total"
          background
          @current-change="(p: number) => $emit('pageChange', p)"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import SummaryCardRow from './SummaryCardRow.vue'

export interface TableColumn {
  prop: string
  label: string
  width?: string
  minWidth?: string
  fixed?: string | boolean
  slot?: string
}

export interface RowAction {
  label: string
  type?: string
  visible?: (row: any) => boolean
  onClick: (row: any) => void
}

const props = withDefaults(defineProps<{
  title: string
  subtitle?: string
  columns: TableColumn[]
  data: any[]
  loading: boolean
  total: number
  page?: number
  pageSize?: number
  searchable?: boolean
  selectable?: boolean
  searchKeyword?: string
  summaryCards?: { label: string; value: string | number; color?: string }[]
  rowActions?: RowAction[]
  rowActionsWidth?: number
}>(), {
  subtitle: '',
  page: 1,
  pageSize: 10,
  searchable: true,
  selectable: false,
  searchKeyword: '',
  rowActionsWidth: 240,
})

const emit = defineEmits<{
  'update:searchKeyword': [val: string]
  'update:page': [val: number]
  search: []
  pageChange: [page: number]
  selectionChange: [rows: any[]]
}>()

const searchKeywordModel = computed({
  get: () => props.searchKeyword,
  set: (val) => emit('update:searchKeyword', val),
})

const currentPageModel = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val),
})
</script>

<style scoped>
.page { }
.page-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px;
}
.page-header h2 {
  font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0;
}
.subtitle { color: #94A3B8; font-size: 13px; margin: 4px 0 0; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }
.table-card { border-radius: 14px; }
.toolbar {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px;
}
.toolbar-left { display: flex; align-items: center; gap: 12px; }
.toolbar-right { display: flex; gap: 8px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
```

- [ ] **Step 3: 创建 ManagementFormDialog 通用表单弹窗**

Create file `expense-web/src/components/ManagementFormDialog.vue`:

```vue
<!-- expense-web/src/components/ManagementFormDialog.vue -->
<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    @closed="$emit('closed')"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      @submit.prevent=""
    >
      <el-form-item
        v-for="field in fields"
        :key="field.prop"
        :label="field.label"
        :prop="field.prop"
      >
        <!-- Text input -->
        <el-input
          v-if="field.type === 'text' || !field.type"
          v-model="formData[field.prop]"
          :placeholder="field.placeholder ?? '请输入' + field.label"
        />
        <!-- Number input -->
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="formData[field.prop]"
          :min="field.min ?? 0"
          :precision="field.precision ?? 2"
          style="width: 100%"
        />
        <!-- Select -->
        <el-select
          v-else-if="field.type === 'select'"
          v-model="formData[field.prop]"
          :placeholder="field.placeholder ?? '请选择' + field.label"
          style="width: 100%"
        >
          <el-option
            v-for="opt in field.options"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <!-- Date -->
        <el-input
          v-else-if="field.type === 'date'"
          v-model="formData[field.prop]"
          type="date"
        />
        <!-- Textarea -->
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="formData[field.prop]"
          type="textarea"
          :rows="field.rows ?? 3"
          :placeholder="field.placeholder ?? '请输入' + field.label"
        />
        <!-- Switch -->
        <el-switch
          v-else-if="field.type === 'switch'"
          v-model="formData[field.prop]"
        />
        <!-- Password -->
        <el-input
          v-else-if="field.type === 'password'"
          v-model="formData[field.prop]"
          type="password"
          show-password
          placeholder="留空则不修改密码"
        />
        <!-- Slot for custom content -->
        <slot v-else-if="field.type === 'custom'" :name="'field-' + field.prop" :form="formData" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('cancel')" :disabled="submitting">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :loading="submitting">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'

export interface FormField {
  prop: string
  label: string
  type?: 'text' | 'number' | 'select' | 'date' | 'textarea' | 'switch' | 'password' | 'custom'
  placeholder?: string
  min?: number
  precision?: number
  rows?: number
  options?: { label: string; value: any }[]
}

defineProps<{
  visible: boolean
  title: string
  fields: FormField[]
  formData: Record<string, any>
  rules?: FormRules
  submitting?: boolean
  width?: string
  labelWidth?: string
  formRef?: FormInstance | null
}>()

defineEmits<{
  submit: []
  cancel: []
  closed: []
}>()
</script>
```

- [ ] **Step 4: Commit**

```bash
git add expense-web/src/components/ManagementTable.vue expense-web/src/components/SummaryCardRow.vue expense-web/src/components/ManagementFormDialog.vue
git commit -m "feat(web): add ManagementTable, ManagementFormDialog, SummaryCardRow components"
```

---

### Task 4: API 模块批量创建

**Files:**
- Create: `expense-web/src/api/user.ts`
- Create: `expense-web/src/api/role.ts`
- Create: `expense-web/src/api/department.ts`
- Create: `expense-web/src/api/policy.ts`
- Create: `expense-web/src/api/tenant.ts`
- Create: `expense-web/src/api/payment.ts`

- [ ] **Step 1: 创建 user.ts**

```typescript
import request from './request'

export function getUserPage(params: any) {
  return request.get('/system/user/page', { params })
}
export function getUserDetail(id: number) {
  return request.get(`/system/user/${id}`)
}
export function createUser(data: any) {
  return request.post('/system/user', data)
}
export function updateUser(id: number, data: any) {
  return request.put(`/system/user/${id}`, data)
}
export function deleteUser(id: number) {
  return request.delete(`/system/user/${id}`)
}
export function updateUserStatus(id: number, status: string) {
  return request.put(`/system/user/${id}/status`, { status })
}
export function resetPassword(id: number) {
  return request.put(`/system/user/${id}/reset-password`)
}
export function getUserRoles(id: number) {
  return request.get(`/system/user/${id}/roles`)
}
```

- [ ] **Step 2: 创建 role.ts**

```typescript
import request from './request'

export function getRolePage(params: any) {
  return request.get('/system/role/page', { params })
}
export function getRoleDetail(id: number) {
  return request.get(`/system/role/${id}`)
}
export function createRole(data: any) {
  return request.post('/system/role', data)
}
export function updateRole(id: number, data: any) {
  return request.put(`/system/role/${id}`, data)
}
export function deleteRole(id: number) {
  return request.delete(`/system/role/${id}`)
}
export function assignRoleUsers(roleId: number, userIds: number[]) {
  return request.post(`/system/role/${roleId}/users`, { userIds })
}
export function assignRolePermissions(roleId: number, permissionIds: number[]) {
  return request.post(`/system/role/${roleId}/permissions`, { permissionIds })
}
export function getRolePermissions(roleId: number) {
  return request.get(`/system/role/${roleId}/permissions`)
}
export function getAllPermissions() {
  return request.get('/system/permission/all')
}
```

- [ ] **Step 3: 创建 department.ts**

```typescript
import request from './request'

export function getDepartmentTree() {
  return request.get('/system/department/tree')
}
export function createDepartment(data: any) {
  return request.post('/system/department', data)
}
export function updateDepartment(id: number, data: any) {
  return request.put(`/system/department/${id}`, data)
}
export function deleteDepartment(id: number) {
  return request.delete(`/system/department/${id}`)
}
export function getEmployeePage(params: any) {
  return request.get('/system/employee/page', { params })
}
```

- [ ] **Step 4: 创建 policy.ts**

```typescript
import request from './request'

export function getPolicyPage(params: any) {
  return request.get('/expense/policy/page', { params })
}
export function getPolicyDetail(id: number) {
  return request.get(`/expense/policy/${id}`)
}
export function createPolicy(data: any) {
  return request.post('/expense/policy', data)
}
export function updatePolicy(id: number, data: any) {
  return request.put(`/expense/policy/${id}`, data)
}
export function deletePolicy(id: number) {
  return request.delete(`/expense/policy/${id}`)
}
```

- [ ] **Step 5: 创建 tenant.ts**

```typescript
import request from './request'

export function getTenantPage(params: any) {
  return request.get('/system/tenant/page', { params })
}
export function getTenantDetail(id: number) {
  return request.get(`/system/tenant/${id}`)
}
export function createTenant(data: any) {
  return request.post('/system/tenant', data)
}
export function updateTenant(id: number, data: any) {
  return request.put(`/system/tenant/${id}`, data)
}
export function deleteTenant(id: number) {
  return request.delete(`/system/tenant/${id}`)
}
export function updateTenantStatus(id: number, status: string) {
  return request.put(`/system/tenant/${id}/status`, { status })
}
```

- [ ] **Step 6: 创建 payment.ts**

```typescript
import request from './request'

export function getPaymentPage(params: any) {
  return request.get('/expense/payment/page', { params })
}
export function executePayment(reportId: number) {
  return request.post(`/expense/payment/${reportId}/pay`)
}
```

- [ ] **Step 7: Commit**

```bash
git add expense-web/src/api/user.ts expense-web/src/api/role.ts expense-web/src/api/department.ts expense-web/src/api/policy.ts expense-web/src/api/tenant.ts expense-web/src/api/payment.ts
git commit -m "feat(web): add API modules for user, role, department, policy, tenant, payment"
```

---

### Task 5: UserManagement 页面

**Files:**
- Create: `expense-web/src/views/system/UserManagement.vue`

- [ ] **Step 1: 创建用户管理页面**

```vue
<template>
  <ManagementTable
    title="用户管理"
    subtitle="管理系统用户账号、角色和状态"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    v-model:searchKeyword="searchKeyword"
    :summary-cards="summaryCards"
    :row-actions="rowActions"
    @search="onSearch"
    @page-change="onPageChange"
  >
    <template #header-actions>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建用户
      </el-button>
    </template>

    <template #status="{ row }">
      <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain" size="small">
        {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
      </el-tag>
    </template>

    <template #roles="{ row }">
      <el-tag v-for="r in (row.roles ?? [])" :key="r" size="small" style="margin:1px">
        {{ r }}
      </el-tag>
    </template>
  </ManagementTable>

  <ManagementFormDialog
    :visible="form.dialogVisible.value"
    :title="form.dialogTitle.value"
    :fields="formFields"
    :form-data="form.formData"
    :rules="formRules"
    :submitting="form.submitting.value"
    :form-ref="form.formRef.value"
    @submit="form.handleSubmit()"
    @cancel="form.handleClose()"
  />
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import ManagementTable from '@/components/ManagementTable.vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { useManagementForm } from '@/composables/useManagementForm'
import { getUserPage, createUser, updateUser, deleteUser, getUserDetail, updateUserStatus, resetPassword } from '@/api/user'
import type { FormRules } from 'element-plus'

const {
  tableData, loading, total, page, pageSize, searchKeyword,
  loadData, onSearch, onReset, onPageChange, onSizeChange, handleDelete,
} = useManagementTable({ fetchApi: getUserPage, deleteApi: deleteUser })

const form = useManagementForm({
  createApi: createUser,
  updateApi: updateUser,
  getDetailApi: getUserDetail,
  defaultForm: { username: '', realName: '', password: '', phone: '', email: '', status: 'ACTIVE' },
  onSuccess: loadData,
})

const columns = [
  { prop: 'username', label: '用户名', width: '140' },
  { prop: 'realName', label: '姓名', width: '120' },
  { prop: 'phone', label: '手机号', width: '140' },
  { prop: 'email', label: '邮箱', minWidth: '180' },
  { prop: 'status', label: '状态', width: '90', slot: 'status' },
  { prop: 'roles', label: '角色', minWidth: '160', slot: 'roles' },
]

const formFields = [
  { prop: 'username', label: '用户名', placeholder: '登录账号' },
  { prop: 'realName', label: '姓名' },
  { prop: 'password', label: '密码', type: 'password' as const },
  { prop: 'phone', label: '手机号' },
  { prop: 'email', label: '邮箱' },
  { prop: 'status', label: '启用', type: 'switch' as const },
]

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

const summaryCards = computed(() => {
  const active = tableData.value.filter((u: any) => u.status === 'ACTIVE').length
  const total = tableData.value.length
  return [
    { label: '总用户', value: total, color: '#3B82F6' },
    { label: '启用', value: active, color: '#10B981' },
    { label: '禁用', value: total - active, color: '#EF4444' },
  ]
})

const rowActions = [
  { label: '编辑', onClick: (row: any) => form.handleEdit(row.id) },
  {
    label: '重置密码',
    type: 'warning',
    onClick: async (row: any) => { await resetPassword(row.id); loadData() },
  },
  {
    label: '启用/禁用',
    type: 'warning',
    onClick: async (row: any) => {
      const s = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
      await updateUserStatus(row.id, s)
      loadData()
    },
  },
  {
    label: '删除',
    type: 'danger',
    visible: (row: any) => row.username !== 'admin',
    onClick: (row: any) => handleDelete(row.id, row.realName),
  },
]

onMounted(() => loadData())
</script>
```

- [ ] **Step 2: Commit**

```bash
git add expense-web/src/views/system/UserManagement.vue
git commit -m "feat(web): add UserManagement page with CRUD + status + reset password"
```

---

### Task 6: RoleManagement 页面

**Files:**
- Create: `expense-web/src/views/system/RoleManagement.vue`

- [ ] **Step 1: 创建角色管理页面（含权限分配面板）**

```vue
<template>
  <ManagementTable
    title="角色管理"
    subtitle="管理角色及对应的菜单/按钮权限"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    v-model:searchKeyword="searchKeyword"
    :row-actions="rowActions"
    @search="onSearch"
    @page-change="onPageChange"
  >
    <template #header-actions>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建角色
      </el-button>
    </template>

    <template #roleType="{ row }">
      <el-tag :type="row.roleType === 1 ? '' : 'info'" effect="plain" size="small">
        {{ row.roleType === 1 ? '系统内置' : '自定义' }}
      </el-tag>
    </template>
  </ManagementTable>

  <ManagementFormDialog
    :visible="form.dialogVisible.value"
    :title="form.dialogTitle.value"
    :fields="formFields"
    :form-data="form.formData"
    :rules="formRules"
    :submitting="form.submitting.value"
    @submit="form.handleSubmit()"
    @cancel="form.handleClose()"
  />

  <!-- Permission assignment dialog -->
  <el-dialog v-model="permDialogVisible" title="分配权限" width="600px" :close-on-click-modal="false">
    <el-tree
      ref="permTreeRef"
      :data="permTree"
      show-checkbox
      node-key="id"
      default-expand-all
      :default-checked-keys="checkedPermIds"
      :props="{ label: 'name', children: 'children' }"
    />
    <template #footer>
      <el-button @click="permDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSavePerms" :loading="savingPerms">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import ManagementTable from '@/components/ManagementTable.vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { useManagementForm } from '@/composables/useManagementForm'
import {
  getRolePage, createRole, updateRole, deleteRole, getRoleDetail,
  getRolePermissions, assignRolePermissions, getAllPermissions,
} from '@/api/role'
import type { FormRules } from 'element-plus'

const {
  tableData, loading, total, page, pageSize, searchKeyword,
  loadData, onSearch, onPageChange, handleDelete,
} = useManagementTable({ fetchApi: getRolePage, deleteApi: deleteRole })

const form = useManagementForm({
  createApi: createRole,
  updateApi: updateRole,
  getDetailApi: getRoleDetail,
  defaultForm: { roleName: '', roleCode: '', roleType: 2, remark: '' },
  onSuccess: loadData,
})

const columns = [
  { prop: 'roleName', label: '角色名称', width: '140' },
  { prop: 'roleCode', label: '角色编码', width: '160' },
  { prop: 'roleType', label: '类型', width: '100', slot: 'roleType' },
  { prop: 'remark', label: '备注', minWidth: '200' },
]

const formFields = [
  { prop: 'roleName', label: '角色名称' },
  { prop: 'roleCode', label: '角色编码' },
  { prop: 'remark', label: '备注', type: 'textarea' as const },
]

const formRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

// Permission assignment
const permDialogVisible = ref(false)
const permTree = ref<any[]>([])
const checkedPermIds = ref<number[]>([])
const savingPerms = ref(false)
let currentPermRoleId: number | null = null

const rowActions = [
  { label: '编辑', onClick: (row: any) => form.handleEdit(row.id) },
  {
    label: '分配权限',
    type: 'success',
    onClick: async (row: any) => {
      currentPermRoleId = row.id
      const [treeRes, permRes] = await Promise.all([
        getAllPermissions(),
        getRolePermissions(row.id),
      ])
      permTree.value = treeRes.data ?? []
      checkedPermIds.value = (permRes.data ?? []).map((p: any) => p.id)
      permDialogVisible.value = true
    },
  },
  {
    label: '删除',
    type: 'danger',
    visible: (row: any) => row.roleType !== 1,
    onClick: (row: any) => handleDelete(row.id, row.roleName),
  },
]

async function handleSavePerms() {
  if (!currentPermRoleId) return
  savingPerms.value = true
  try {
    await assignRolePermissions(currentPermRoleId, checkedPermIds.value)
    ElMessage.success('权限已更新')
    permDialogVisible.value = false
  } finally {
    savingPerms.value = false
  }
}

onMounted(() => loadData())
</script>
```

- [ ] **Step 2: Commit**

```bash
git add expense-web/src/views/system/RoleManagement.vue
git commit -m "feat(web): add RoleManagement page with permission assignment tree"
```

---

### Task 7: DepartmentManagement 页面（树形结构）

**Files:**
- Create: `expense-web/src/views/system/DepartmentManagement.vue`

- [ ] **Step 1: 创建部门管理页面（树形表格 + 员工管理）**

```vue
<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>部门管理</h2>
        <p class="subtitle">管理组织架构及部门员工</p>
      </div>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建部门
      </el-button>
    </div>

    <el-row :gutter="16">
      <!-- Department Tree -->
      <el-col :span="10">
        <el-card class="table-card">
          <template #header><span>组织架构</span></template>
          <el-tree
            :data="deptTree"
            :props="{ label: 'deptName', children: 'children' }"
            node-key="id"
            default-expand-all
            highlight-current
            @node-click="onDeptClick"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span>{{ data.deptName }}</span>
                <span class="tree-node-actions">
                  <el-button type="primary" link size="small" @click.stop="form.handleEdit(data.id)">编辑</el-button>
                  <el-button type="danger" link size="small" @click.stop="handleDeptDelete(data)">删除</el-button>
                </span>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="deptTree.length === 0" description="暂无部门" :image-size="60" />
        </el-card>
      </el-col>

      <!-- Employee List -->
      <el-col :span="14">
        <el-card class="table-card">
          <template #header>
            <span>{{ currentDept ? currentDept.deptName + ' - 员工' : '选择部门查看员工' }}</span>
          </template>
          <el-table :data="employees" stripe v-loading="empLoading" class="data-table">
            <el-table-column prop="userName" label="姓名" width="120" />
            <el-table-column prop="phone" label="手机号" width="140" />
            <el-table-column prop="email" label="邮箱" min-width="180" />
            <el-table-column prop="position" label="职位" width="120" />
          </el-table>
          <el-empty v-if="!empLoading && employees.length === 0" description="暂无员工" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <ManagementFormDialog
      :visible="form.dialogVisible.value"
      :title="form.dialogTitle.value"
      :fields="formFields"
      :form-data="form.formData"
      :rules="formRules"
      :submitting="form.submitting.value"
      @submit="form.handleSubmit()"
      @cancel="form.handleClose()"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementForm } from '@/composables/useManagementForm'
import {
  getDepartmentTree, createDepartment, updateDepartment, deleteDepartment, getEmployeePage,
} from '@/api/department'
import type { FormRules } from 'element-plus'

const deptTree = ref<any[]>([])
const employees = ref<any[]>([])
const empLoading = ref(false)
const currentDept = ref<any>(null)

const form = useManagementForm({
  createApi: createDepartment,
  updateApi: updateDepartment,
  getDetailApi: async (id: number) => {
    const findInTree = (nodes: any[]): any => {
      for (const n of nodes) {
        if (n.id === id) return n
        if (n.children) { const r = findInTree(n.children); if (r) return r }
      }
      return null
    }
    return { data: findInTree(deptTree.value) ?? {} }
  },
  defaultForm: { deptName: '', deptCode: '', parentId: null, leaderId: null, sortOrder: 0 },
  onSuccess: loadDeptTree,
})

const formFields = [
  { prop: 'deptName', label: '部门名称' },
  { prop: 'deptCode', label: '部门编码' },
  { prop: 'sortOrder', label: '排序号', type: 'number' as const },
]

const formRules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

async function loadDeptTree() {
  const res = await getDepartmentTree()
  deptTree.value = res.data ?? []
}

async function onDeptClick(data: any) {
  currentDept.value = data
  empLoading.value = true
  try {
    const res = await getEmployeePage({ deptId: data.id, page: 1, size: 100 })
    employees.value = res.data?.records ?? []
  } finally {
    empLoading.value = false
  }
}

async function handleDeptDelete(data: any) {
  try {
    await ElMessageBox.confirm(`确认删除部门 "${data.deptName}"？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteDepartment(data.id)
  loadDeptTree()
}

onMounted(() => loadDeptTree())
</script>

<style scoped>
.page { }
.page-header {
  display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px;
}
.page-header h2 {
  font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0;
}
.subtitle { color: #94A3B8; font-size: 13px; margin: 4px 0 0; }
.table-card { border-radius: 14px; height: 100%; }
.tree-node {
  flex: 1; display: flex; justify-content: space-between; align-items: center; padding-right: 8px;
}
.tree-node-actions { display: none; }
:deep(.el-tree-node__content:hover .tree-node-actions) { display: inline-flex; gap: 4px; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add expense-web/src/views/system/DepartmentManagement.vue
git commit -m "feat(web): add DepartmentManagement page with tree + employee list"
```

---

### Task 8: PolicyManagement 页面 + TenantManagement 页面

**Files:**
- Create: `expense-web/src/views/system/PolicyManagement.vue`
- Create: `expense-web/src/views/system/TenantManagement.vue`

- [ ] **Step 1: 创建差旅标准管理页面**

```vue
<template>
  <ManagementTable
    title="差旅标准"
    subtitle="管理各项费用的报销上限和合规规则"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    :searchable="false"
    :row-actions="rowActions"
    @page-change="onPageChange"
  >
    <template #header-actions>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建标准
      </el-button>
    </template>

    <template #maxAmount="{ row }">
      <span class="amount">&yen;{{ row.maxAmount }}</span>
    </template>

    <template #cityTier="{ row }">
      <el-tag v-if="row.cityTier" size="small" effect="plain">{{ row.cityTier }}</el-tag>
      <span v-else style="color:#94a3b8">—</span>
    </template>
  </ManagementTable>

  <ManagementFormDialog
    :visible="form.dialogVisible.value"
    :title="form.dialogTitle.value"
    :fields="formFields"
    :form-data="form.formData"
    :rules="formRules"
    :submitting="form.submitting.value"
    @submit="form.handleSubmit()"
    @cancel="form.handleClose()"
  />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import ManagementTable from '@/components/ManagementTable.vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { useManagementForm } from '@/composables/useManagementForm'
import { getPolicyPage, createPolicy, updatePolicy, deletePolicy, getPolicyDetail } from '@/api/policy'
import type { FormRules } from 'element-plus'

const {
  tableData, loading, total, page,
  loadData, onPageChange, handleDelete,
} = useManagementTable({ fetchApi: getPolicyPage, deleteApi: deletePolicy })

const form = useManagementForm({
  createApi: createPolicy,
  updateApi: updatePolicy,
  getDetailApi: getPolicyDetail,
  defaultForm: { expenseType: '', maxAmount: 0, dailyLimit: 0, cityTier: '', remark: '' },
  onSuccess: loadData,
})

const columns = [
  { prop: 'expenseType', label: '费用类型', width: '140' },
  { prop: 'maxAmount', label: '单次上限', width: '130', slot: 'maxAmount' },
  { prop: 'dailyLimit', label: '日限额', width: '120' },
  { prop: 'cityTier', label: '城市等级', width: '110', slot: 'cityTier' },
  { prop: 'remark', label: '备注', minWidth: '180' },
]

const expenseTypes = [
  { label: 'TRANSPORT - 交通', value: 'TRANSPORT' },
  { label: 'HOTEL - 住宿', value: 'HOTEL' },
  { label: 'MEAL - 餐费', value: 'MEAL' },
  { label: 'OTHER - 其他', value: 'OTHER' },
]

const cityTiers = [
  { label: '一线城市', value: 'TIER1' },
  { label: '二线城市', value: 'TIER2' },
  { label: '三线及其他', value: 'TIER3' },
]

const formFields = [
  { prop: 'expenseType', label: '费用类型', type: 'select' as const, options: expenseTypes },
  { prop: 'maxAmount', label: '单次上限(元)', type: 'number' as const, min: 0, precision: 2 },
  { prop: 'dailyLimit', label: '日限额(元)', type: 'number' as const, min: 0, precision: 2 },
  { prop: 'cityTier', label: '适用城市', type: 'select' as const, options: [{ label: '全部', value: '' }, ...cityTiers] },
  { prop: 'remark', label: '备注', type: 'textarea' as const },
]

const formRules: FormRules = {
  expenseType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
  maxAmount: [{ required: true, message: '请输入单次上限', trigger: 'blur' }],
}

const rowActions = [
  { label: '编辑', onClick: (row: any) => form.handleEdit(row.id) },
  { label: '删除', type: 'danger', onClick: (row: any) => handleDelete(row.id, row.expenseType) },
]

onMounted(() => loadData())
</script>

<style scoped>
.amount { font-weight: 600; color: #1E3A5F; }
</style>
```

- [ ] **Step 2: 创建租户管理页面**

```vue
<template>
  <ManagementTable
    title="租户管理"
    subtitle="管理多租户及租户状态"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    v-model:searchKeyword="searchKeyword"
    :row-actions="rowActions"
    @search="onSearch"
    @page-change="onPageChange"
  >
    <template #header-actions>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建租户
      </el-button>
    </template>

    <template #status="{ row }">
      <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" effect="plain" size="small">
        {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
      </el-tag>
    </template>
  </ManagementTable>

  <ManagementFormDialog
    :visible="form.dialogVisible.value"
    :title="form.dialogTitle.value"
    :fields="formFields"
    :form-data="form.formData"
    :rules="formRules"
    :submitting="form.submitting.value"
    @submit="form.handleSubmit()"
    @cancel="form.handleClose()"
  />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import ManagementTable from '@/components/ManagementTable.vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { useManagementForm } from '@/composables/useManagementForm'
import { getTenantPage, createTenant, updateTenant, deleteTenant, getTenantDetail, updateTenantStatus } from '@/api/tenant'
import type { FormRules } from 'element-plus'

const {
  tableData, loading, total, page, searchKeyword,
  loadData, onSearch, onPageChange, handleDelete,
} = useManagementTable({ fetchApi: getTenantPage, deleteApi: deleteTenant })

const form = useManagementForm({
  createApi: createTenant,
  updateApi: updateTenant,
  getDetailApi: getTenantDetail,
  defaultForm: { tenantName: '', tenantCode: '', contactName: '', contactPhone: '', status: 'ACTIVE' },
  onSuccess: loadData,
})

const columns = [
  { prop: 'tenantCode', label: '租户编码', width: '140' },
  { prop: 'tenantName', label: '租户名称', width: '160' },
  { prop: 'contactName', label: '联系人', width: '120' },
  { prop: 'contactPhone', label: '联系电话', width: '140' },
  { prop: 'status', label: '状态', width: '90', slot: 'status' },
]

const formFields = [
  { prop: 'tenantName', label: '租户名称' },
  { prop: 'tenantCode', label: '租户编码' },
  { prop: 'contactName', label: '联系人' },
  { prop: 'contactPhone', label: '联系电话' },
  { prop: 'status', label: '启用', type: 'switch' as const },
]

const formRules: FormRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantCode: [{ required: true, message: '请输入租户编码', trigger: 'blur' }],
}

const rowActions = [
  { label: '编辑', onClick: (row: any) => form.handleEdit(row.id) },
  {
    label: '启用/禁用',
    type: 'warning',
    onClick: async (row: any) => {
      const s = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
      await updateTenantStatus(row.id, s)
      loadData()
    },
  },
  { label: '删除', type: 'danger', onClick: (row: any) => handleDelete(row.id, row.tenantName) },
]

onMounted(() => loadData())
</script>
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/views/system/PolicyManagement.vue expense-web/src/views/system/TenantManagement.vue
git commit -m "feat(web): add PolicyManagement and TenantManagement pages"
```

---

### Task 9: PaymentManagement 页面 + AuditLogViewer 页面

**Files:**
- Create: `expense-web/src/views/finance/PaymentManagement.vue`
- Create: `expense-web/src/views/ai/AuditLogViewer.vue`

- [ ] **Step 1: 创建打款管理页面**

```vue
<template>
  <ManagementTable
    title="打款管理"
    subtitle="查看所有打款记录，执行打款操作"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    :row-actions="rowActions"
    :summary-cards="summaryCards"
    @page-change="onPageChange"
  >
    <template #amount="{ row }">
      <span class="amount">&yen;{{ row.amount ?? row.paymentAmount }}</span>
    </template>

    <template #status="{ row }">
      <el-tag :type="row.status === 'PAID' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" effect="plain" size="small">
        {{ row.status === 'PAID' ? '已打款' : row.status === 'PENDING' ? '待打款' : row.status }}
      </el-tag>
    </template>

    <template #payee="{ row }">
      {{ row.payee ?? row.applicantName ?? '—' }}
    </template>
  </ManagementTable>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import ManagementTable from '@/components/ManagementTable.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { getPaymentPage, executePayment } from '@/api/payment'

const {
  tableData, loading, total, page,
  loadData, onPageChange,
} = useManagementTable({ fetchApi: getPaymentPage })

const columns = [
  { prop: 'reportNo', label: '报销编号', width: '190' },
  { prop: 'payee', label: '收款人', width: '120', slot: 'payee' },
  { prop: 'amount', label: '金额', width: '130', slot: 'amount' },
  { prop: 'paymentTime', label: '打款时间', width: '180' },
  { prop: 'status', label: '状态', width: '100', slot: 'status' },
  { prop: 'remark', label: '备注', minWidth: '160' },
]

const summaryCards = computed(() => {
  const data = tableData.value
  const paid = data.filter((r: any) => r.status === 'PAID').length
  const pending = data.filter((r: any) => r.status === 'PENDING').length
  const totalAmt = data.reduce((s: number, r: any) => s + (r.amount ?? r.paymentAmount ?? 0), 0)
  return [
    { label: '总记录', value: data.length, color: '#3B82F6' },
    { label: '已打款', value: paid, color: '#10B981' },
    { label: '待打款', value: pending, color: '#F59E0B' },
    { label: '总金额', value: `¥${totalAmt.toFixed(0)}`, color: '#8B5CF6' },
  ]
})

const rowActions = [
  {
    label: '执行打款',
    type: 'success',
    visible: (row: any) => row.status === 'PENDING',
    onClick: async (row: any) => {
      await executePayment(row.reportId ?? row.id)
      ElMessage.success('打款成功')
      loadData()
    },
  },
]

onMounted(() => loadData())
</script>

<style scoped>
.amount { font-weight: 600; color: #1E3A5F; }
</style>
```

- [ ] **Step 2: 创建审计日志/AI审单历史页面**

```vue
<template>
  <ManagementTable
    title="审单日志"
    subtitle="AI 审单历史记录及审批操作审计"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    :searchable="true"
    v-model:searchKeyword="searchKeyword"
    @search="onSearch"
    @page-change="onPageChange"
  >
    <template #result="{ row }">
      <el-tag
        :type="row.reviewResult === 'APPROVED' ? 'success' : row.reviewResult === 'REJECTED' ? 'danger' : 'warning'"
        effect="plain"
        size="small"
      >
        {{ row.reviewResult ?? row.result ?? '—' }}
      </el-tag>
    </template>

    <template #riskLevel="{ row }">
      <el-tag
        v-if="row.riskLevel"
        :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'MEDIUM' ? 'warning' : 'success'"
        size="small"
      >
        {{ row.riskLevel }}
      </el-tag>
      <span v-else style="color:#94a3b8">—</span>
    </template>

    <template #confidence="{ row }">
      <el-progress
        v-if="row.confidence != null"
        :percentage="Math.round((row.confidence ?? 0) * 100)"
        :stroke-width="8"
        style="width:80px"
      />
      <span v-else style="color:#94a3b8">—</span>
    </template>
  </ManagementTable>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import ManagementTable from '@/components/ManagementTable.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { getAuditLogPage } from '@/api/ai'

const {
  tableData, loading, total, page, searchKeyword,
  loadData, onSearch, onPageChange,
} = useManagementTable({ fetchApi: getAuditLogPage })

const columns = [
  { prop: 'businessNo', label: '单据编号', width: '190' },
  { prop: 'businessType', label: '类型', width: '120' },
  { prop: 'reviewResult', label: '审单结果', width: '120', slot: 'result' },
  { prop: 'riskLevel', label: '风险等级', width: '100', slot: 'riskLevel' },
  { prop: 'confidence', label: '置信度', width: '120', slot: 'confidence' },
  { prop: 'createdAt', label: '审单时间', width: '180' },
  { prop: 'operatorName', label: '操作人', width: '120' },
]

onMounted(() => loadData())
</script>
```

Note: AuditLogViewer uses `getAuditLogPage` from `ai.ts`. We'll need to add this to `expense-web/src/api/ai.ts` as well.

- [ ] **Step 3: 在 ai.ts 中添加审计日志 API**

Read `expense-web/src/api/ai.ts`, then add at the end:

```typescript
export function getAuditLogPage(params: any) {
  return request.get('/ai/review/logs/page', { params })
}
export function getRuleResults(taskId: string) {
  return request.get(`/approval/tasks/${taskId}/rule-results`)
}
```

- [ ] **Step 4: Commit**

```bash
git add expense-web/src/views/finance/PaymentManagement.vue expense-web/src/views/ai/AuditLogViewer.vue expense-web/src/api/ai.ts
git commit -m "feat(web): add PaymentManagement, AuditLogViewer pages"
```

---

### Task 10: 路由 + 侧边栏导航更新

**Files:**
- Modify: `expense-web/src/router/index.ts`
- Modify: `expense-web/src/layouts/MainLayout.vue`

- [ ] **Step 1: 更新路由**

In `expense-web/src/router/index.ts`, add these child routes after the existing `notification` route:

```typescript
// System management
{
  path: 'system/users', name: 'UserManagement',
  component: () => import('../views/system/UserManagement.vue'),
  meta: { permission: 'system:user' }
},
{
  path: 'system/roles', name: 'RoleManagement',
  component: () => import('../views/system/RoleManagement.vue'),
  meta: { permission: 'system:role' }
},
{
  path: 'system/departments', name: 'DepartmentManagement',
  component: () => import('../views/system/DepartmentManagement.vue'),
  meta: { permission: 'system:dept' }
},
{
  path: 'system/tenants', name: 'TenantManagement',
  component: () => import('../views/system/TenantManagement.vue'),
  meta: { permission: 'system:tenant' }
},
{
  path: 'system/policies', name: 'PolicyManagement',
  component: () => import('../views/system/PolicyManagement.vue'),
  meta: { permission: 'policy:view' }
},
// Finance management
{
  path: 'finance/payments', name: 'PaymentManagement',
  component: () => import('../views/finance/PaymentManagement.vue'),
  meta: { permission: 'finance:payment' }
},
{
  path: 'finance/budgets', name: 'BudgetManagement',
  component: () => import('../views/finance/BudgetManagement.vue'),
  meta: { permission: 'finance:budget' }
},
// AI audit log
{
  path: 'ai-audit-logs', name: 'AuditLogViewer',
  component: () => import('../views/ai/AuditLogViewer.vue'),
  meta: { permission: 'ai:review' }
},
```

- [ ] **Step 2: 更新侧边栏**

In `expense-web/src/layouts/MainLayout.vue`, add two new menu groups before the closing `</el-menu>` tag (before the AI services sub-menu):

Insert after the notification menu item:

```html
<!-- System Management -->
<el-sub-menu index="system" v-if="perm.has('system:user') || perm.has('system:role') || perm.has('system:dept') || perm.has('system:tenant') || perm.has('policy:view')">
  <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
  <el-menu-item index="/system/users" v-if="perm.has('system:user')">用户管理</el-menu-item>
  <el-menu-item index="/system/roles" v-if="perm.has('system:role')">角色管理</el-menu-item>
  <el-menu-item index="/system/departments" v-if="perm.has('system:dept')">部门管理</el-menu-item>
  <el-menu-item index="/system/tenants" v-if="perm.has('system:tenant')">租户管理</el-menu-item>
  <el-menu-item index="/system/policies" v-if="perm.has('policy:view')">差旅标准</el-menu-item>
</el-sub-menu>

<!-- Finance Management -->
<el-sub-menu index="finance" v-if="perm.has('finance:payment') || perm.has('finance:budget')">
  <template #title><el-icon><Wallet /></el-icon><span>财务管理</span></template>
  <el-menu-item index="/finance/payments" v-if="perm.has('finance:payment')">打款管理</el-menu-item>
  <el-menu-item index="/finance/budgets" v-if="perm.has('finance:budget')">预算管理</el-menu-item>
</el-sub-menu>
```

Also add `Setting` and `Wallet` to the icons import:

```typescript
import { SwitchButton, Setting, Wallet } from '@element-plus/icons-vue'
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/router/index.ts expense-web/src/layouts/MainLayout.vue
git commit -m "feat(web): add system management and finance management routes + sidebar menus"
```

---

### Task 11: 部门预算管理 — 后端

**Files:**
- Create: `expense-service/src/main/java/com/expenseflow/expense/entity/ExDepartmentBudget.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/mapper/ExDepartmentBudgetMapper.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/service/ExDepartmentBudgetService.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/service/impl/ExDepartmentBudgetServiceImpl.java`
- Create: `expense-service/src/main/java/com/expenseflow/expense/controller/BudgetController.java`
- Modify: `expense-service/.../service/impl/PaymentRecordServiceImpl.java`
- Modify: `sql/init.sql`

- [ ] **Step 1: 创建 Entity**

```java
package com.expenseflow.expense.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_department_budget")
public class ExDepartmentBudget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long departmentId;
    private Integer budgetYear;
    private Integer budgetQuarter;
    private BigDecimal totalAmount;
    private BigDecimal usedAmount;
    private BigDecimal alertThreshold;
    private String status;
    private String remark;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private Long tenantId;
}
```

- [ ] **Step 2: 创建 Mapper**

```java
package com.expenseflow.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.expenseflow.expense.entity.ExDepartmentBudget;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExDepartmentBudgetMapper extends BaseMapper<ExDepartmentBudget> {
}
```

- [ ] **Step 3: 创建 Service 接口和实现**

```java
package com.expenseflow.expense.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.expenseflow.expense.entity.ExDepartmentBudget;

public interface ExDepartmentBudgetService extends IService<ExDepartmentBudget> {
    /**
     * 扣减部门预算，预算不足时抛异常
     */
    void deductBudget(Long departmentId, BigDecimal amount, Long tenantId);

    /**
     * 查询部门当前可用预算
     */
    ExDepartmentBudget getCurrentBudget(Long departmentId, Long tenantId);
}
```

```java
package com.expenseflow.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.expenseflow.common.exception.BusinessException;
import com.expenseflow.expense.entity.ExDepartmentBudget;
import com.expenseflow.expense.mapper.ExDepartmentBudgetMapper;
import com.expenseflow.expense.service.ExDepartmentBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Year;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExDepartmentBudgetServiceImpl extends ServiceImpl<ExDepartmentBudgetMapper, ExDepartmentBudget>
        implements ExDepartmentBudgetService {

    @Override
    @Transactional
    public void deductBudget(Long departmentId, BigDecimal amount, Long tenantId) {
        ExDepartmentBudget budget = getCurrentBudget(departmentId, tenantId);
        if (budget == null) {
            log.warn("部门(id={})无预算配置，跳过预算校验", departmentId);
            return;
        }
        BigDecimal remaining = budget.getTotalAmount().subtract(budget.getUsedAmount());
        if (remaining.compareTo(amount) < 0) {
            throw new BusinessException(String.format(
                "部门预算不足：需 %.2f 元，剩余 %.2f 元", amount, remaining));
        }
        budget.setUsedAmount(budget.getUsedAmount().add(amount));
        updateById(budget);

        BigDecimal ratio = budget.getUsedAmount().divide(budget.getTotalAmount(), 4, BigDecimal.ROUND_HALF_UP);
        if (ratio.compareTo(budget.getAlertThreshold()) >= 0) {
            log.warn("部门(id={})预算使用率已达 {:.0f}%，触发预警", departmentId, ratio.multiply(BigDecimal.valueOf(100)));
        }
    }

    @Override
    public ExDepartmentBudget getCurrentBudget(Long departmentId, Long tenantId) {
        int year = Year.now().getValue();
        LambdaQueryWrapper<ExDepartmentBudget> qw = new LambdaQueryWrapper<>();
        qw.eq(ExDepartmentBudget::getDepartmentId, departmentId)
          .eq(ExDepartmentBudget::getBudgetYear, year)
          .eq(ExDepartmentBudget::getTenantId, tenantId)
          .eq(ExDepartmentBudget::getStatus, "ACTIVE")
          .isNull(ExDepartmentBudget::getBudgetQuarter)
          .last("LIMIT 1");
        return getOne(qw);
    }
}
```

- [ ] **Step 4: 创建 Controller**

```java
package com.expenseflow.expense.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.expenseflow.common.result.Result;
import com.expenseflow.expense.entity.ExDepartmentBudget;
import com.expenseflow.expense.service.ExDepartmentBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/expense/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final ExDepartmentBudgetService budgetService;

    @GetMapping("/page")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Page<ExDepartmentBudget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer budgetYear) {
        LambdaQueryWrapper<ExDepartmentBudget> qw = new LambdaQueryWrapper<>();
        if (departmentId != null) qw.eq(ExDepartmentBudget::getDepartmentId, departmentId);
        if (budgetYear != null) qw.eq(ExDepartmentBudget::getBudgetYear, budgetYear);
        qw.orderByDesc(ExDepartmentBudget::getBudgetYear).orderByAsc(ExDepartmentBudget::getDepartmentId);
        return Result.success(budgetService.page(new Page<>(page, size), qw));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<ExDepartmentBudget> getById(@PathVariable Long id) {
        return Result.success(budgetService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> create(@RequestBody ExDepartmentBudget budget) {
        budget.setUsedAmount(budget.getUsedAmount() != null ? budget.getUsedAmount() : BigDecimal.ZERO);
        budget.setAlertThreshold(budget.getAlertThreshold() != null ? budget.getAlertThreshold() : new BigDecimal("0.80"));
        budget.setStatus("ACTIVE");
        budgetService.save(budget);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> update(@PathVariable Long id, @RequestBody ExDepartmentBudget budget) {
        budget.setId(id);
        budgetService.updateById(budget);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@pms.hasPermission('finance:budget')")
    public Result<Void> delete(@PathVariable Long id) {
        budgetService.removeById(id);
        return Result.success();
    }
}
```

- [ ] **Step 5: 在打款流程中接入预算扣减**

In `PaymentRecordServiceImpl.java`, inject `ExDepartmentBudgetService` and add before the actual payment execution:

```java
private final ExDepartmentBudgetService budgetService;

// In pay() method, before saving payment record:
SysEmployee employee = employeeService.getByUserId(report.getApplicantId());
if (employee != null && employee.getDepartmentId() != null) {
    budgetService.deductBudget(employee.getDepartmentId(), report.getTotalAmount(),
        TenantContextHolder.getTenantId());
}
```

- [ ] **Step 6: 更新 init.sql 添加预算表 DDL**

```sql
-- 部门预算表
DROP TABLE IF EXISTS sys_department_budget;
CREATE TABLE sys_department_budget (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id   BIGINT NOT NULL COMMENT '部门ID',
    budget_year     INT NOT NULL COMMENT '预算年度',
    budget_quarter  TINYINT COMMENT '预算季度 1-4，NULL=年度预算',
    total_amount    DECIMAL(12,2) NOT NULL COMMENT '预算总额',
    used_amount     DECIMAL(12,2) DEFAULT 0.00 COMMENT '已使用金额',
    alert_threshold DECIMAL(5,2) DEFAULT 0.80 COMMENT '告警阈值',
    status          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/FROZEN/CLOSED',
    remark          VARCHAR(255) COMMENT '备注',
    created_by      BIGINT COMMENT '创建人ID',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    tenant_id       BIGINT NOT NULL COMMENT '租户ID',
    INDEX idx_dept_year (department_id, budget_year, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门预算表';
```

- [ ] **Step 7: Commit**

```bash
git add expense-service/src/main/java/com/expenseflow/expense/entity/ExDepartmentBudget.java expense-service/src/main/java/com/expenseflow/expense/mapper/ExDepartmentBudgetMapper.java expense-service/src/main/java/com/expenseflow/expense/service/ExDepartmentBudgetService.java expense-service/src/main/java/com/expenseflow/expense/service/impl/ExDepartmentBudgetServiceImpl.java expense-service/src/main/java/com/expenseflow/expense/controller/BudgetController.java expense-service/src/main/java/com/expenseflow/expense/service/impl/PaymentRecordServiceImpl.java sql/init.sql
git commit -m "feat(expense): add department budget management with auto-deduction on payment"
```

---

### Task 12: BudgetManagement 前端页面

**Files:**
- Create: `expense-web/src/views/finance/BudgetManagement.vue`
- Create: `expense-web/src/api/budget.ts`

- [ ] **Step 1: 创建 budget API**

```typescript
import request from './request'

export function getBudgetPage(params: any) {
  return request.get('/expense/budget/page', { params })
}
export function getBudgetDetail(id: number) {
  return request.get(`/expense/budget/${id}`)
}
export function createBudget(data: any) {
  return request.post('/expense/budget', data)
}
export function updateBudget(id: number, data: any) {
  return request.put(`/expense/budget/${id}`, data)
}
export function deleteBudget(id: number) {
  return request.delete(`/expense/budget/${id}`)
}
```

- [ ] **Step 2: 创建预算管理页面**

```vue
<template>
  <ManagementTable
    title="部门预算"
    subtitle="管理各部门的年度预算及使用情况"
    :columns="columns"
    :data="tableData"
    :loading="loading"
    :total="total"
    v-model:page="page"
    :row-actions="rowActions"
    :summary-cards="summaryCards"
    @page-change="onPageChange"
  >
    <template #header-actions>
      <el-button type="primary" @click="form.handleCreate()">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建预算
      </el-button>
    </template>

    <template #usage="{ row }">
      <div style="display:flex;align-items:center;gap:8px">
        <el-progress
          :percentage="usagePercent(row)"
          :stroke-width="8"
          :color="usagePercent(row) > 80 ? '#EF4444' : usagePercent(row) > 60 ? '#F59E0B' : '#10B981'"
          style="flex:1"
        />
        <span style="font-size:12px;color:#94a3b8;white-space:nowrap">
          ¥{{ row.usedAmount ?? 0 }} / ¥{{ row.totalAmount }}
        </span>
      </div>
    </template>

    <template #totalAmount="{ row }">
      <span class="amount">&yen;{{ row.totalAmount }}</span>
    </template>

    <template #status="{ row }">
      <el-tag
        :type="row.status === 'ACTIVE' ? 'success' : row.status === 'FROZEN' ? 'warning' : 'info'"
        effect="plain"
        size="small"
      >
        {{ row.status === 'ACTIVE' ? '生效中' : row.status === 'FROZEN' ? '已冻结' : row.status }}
      </el-tag>
    </template>
  </ManagementTable>

  <ManagementFormDialog
    :visible="form.dialogVisible.value"
    :title="form.dialogTitle.value"
    :fields="formFields"
    :form-data="form.formData"
    :rules="formRules"
    :submitting="form.submitting.value"
    @submit="form.handleSubmit()"
    @cancel="form.handleClose()"
  />
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import ManagementTable from '@/components/ManagementTable.vue'
import ManagementFormDialog from '@/components/ManagementFormDialog.vue'
import { useManagementTable } from '@/composables/useManagementTable'
import { useManagementForm } from '@/composables/useManagementForm'
import { getBudgetPage, createBudget, updateBudget, deleteBudget, getBudgetDetail } from '@/api/budget'
import type { FormRules } from 'element-plus'

const {
  tableData, loading, total, page,
  loadData, onPageChange, handleDelete,
} = useManagementTable({ fetchApi: getBudgetPage, deleteApi: deleteBudget })

const form = useManagementForm({
  createApi: createBudget,
  updateApi: updateBudget,
  getDetailApi: getBudgetDetail,
  defaultForm: { departmentId: '', budgetYear: new Date().getFullYear(), totalAmount: 0, alertThreshold: 0.80, remark: '' },
  onSuccess: loadData,
})

function usagePercent(row: any): number {
  if (!row.totalAmount || row.totalAmount === 0) return 0
  return Math.round((row.usedAmount ?? 0) / row.totalAmount * 100)
}

const columns = [
  { prop: 'departmentId', label: '部门ID', width: '100' },
  { prop: 'budgetYear', label: '年度', width: '80' },
  { prop: 'totalAmount', label: '预算总额', width: '130', slot: 'totalAmount' },
  { prop: 'usage', label: '使用进度', minWidth: '220', slot: 'usage' },
  { prop: 'status', label: '状态', width: '100', slot: 'status' },
]

const formFields = [
  { prop: 'departmentId', label: '部门ID', type: 'number' as const, precision: 0 },
  { prop: 'budgetYear', label: '预算年度', type: 'number' as const, precision: 0 },
  { prop: 'totalAmount', label: '预算总额(元)', type: 'number' as const, min: 0, precision: 2 },
  { prop: 'alertThreshold', label: '告警阈值', type: 'number' as const, min: 0, precision: 2 },
  { prop: 'remark', label: '备注', type: 'textarea' as const },
]

const formRules: FormRules = {
  departmentId: [{ required: true, message: '请输入部门ID', trigger: 'blur' }],
  budgetYear: [{ required: true, message: '请输入预算年度', trigger: 'blur' }],
  totalAmount: [{ required: true, message: '请输入预算总额', trigger: 'blur' }],
}

const summaryCards = computed(() => {
  const data = tableData.value
  const totalAmt = data.reduce((s: number, r: any) => s + (r.totalAmount ?? 0), 0)
  const usedAmt = data.reduce((s: number, r: any) => s + (r.usedAmount ?? 0), 0)
  const remain = totalAmt - usedAmt
  const alertCount = data.filter((r: any) => r.totalAmount && (r.usedAmount ?? 0) / r.totalAmount >= (r.alertThreshold ?? 0.8)).length
  return [
    { label: '总预算', value: `¥${totalAmt.toFixed(0)}`, color: '#3B82F6' },
    { label: '已使用', value: `¥${usedAmt.toFixed(0)}`, color: '#F59E0B' },
    { label: '剩余', value: `¥${remain.toFixed(0)}`, color: '#10B981' },
    { label: '预警部门', value: alertCount, color: '#EF4444' },
  ]
})

const rowActions = [
  { label: '编辑', onClick: (row: any) => form.handleEdit(row.id) },
  { label: '删除', type: 'danger', onClick: (row: any) => handleDelete(row.id, `预算 #${row.id}`) },
]

onMounted(() => loadData())
</script>

<style scoped>
.amount { font-weight: 600; color: #1E3A5F; }
</style>
```

- [ ] **Step 3: Commit**

```bash
git add expense-web/src/views/finance/BudgetManagement.vue expense-web/src/api/budget.ts
git commit -m "feat(web): add BudgetManagement page with usage progress bars"
```

---

### Task 13: 风控告警增强 + Dashboard 风控卡片

**Files:**
- Modify: `expense-web/src/views/ai/AIReviewView.vue`
- Modify: `expense-web/src/views/dashboard/DashboardView.vue`
- Modify: `approval-service/.../controller/ApprovalTaskController.java`

- [ ] **Step 1: 后端添加规则结果查询接口**

In `ApprovalTaskController.java`, add:

```java
@GetMapping("/tasks/{taskId}/rule-results")
@PreAuthorize("@pms.hasPermission('approval:approve')")
public Result<List<Map<String, Object>>> getRuleResults(@PathVariable String taskId) {
    // Fetch from approval record's rule_results JSON field
    List<Map<String, Object>> results = approvalRecordService.getRuleResultsByTaskId(taskId);
    return Result.success(results);
}
```

- [ ] **Step 2: 增强 AIReviewView 规则明细展示**

In `AIReviewView.vue`, after the existing result display section (`el-descriptions` block), add:

```html
<div v-if="result?.ruleDetails?.length" class="rule-details">
  <el-divider content-position="left">规则检查明细</el-divider>
  <div v-for="rule in result.ruleDetails" :key="rule.name" class="rule-item">
    <el-icon :size="16" :color="rule.action === 'PASS' ? '#10B981' : rule.action === 'WARN' ? '#F59E0B' : '#EF4444'">
      <component :is="rule.action === 'PASS' ? 'SuccessFilled' : rule.action === 'WARN' ? 'WarningFilled' : 'CircleCloseFilled'" />
    </el-icon>
    <span class="rule-name">{{ rule.name }}</span>
    <el-tag :type="rule.action === 'PASS' ? 'success' : rule.action === 'WARN' ? 'warning' : 'danger'" size="small">
      {{ rule.action === 'PASS' ? '通过' : rule.action === 'WARN' ? '警告' : '阻止' }}
    </el-tag>
    <span v-if="rule.reason" class="rule-reason">{{ rule.reason }}</span>
  </div>
</div>
```

And add styles:

```css
.rule-details { margin-top: 8px; }
.rule-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: #f8fafc; border-radius: 8px; margin-bottom: 6px;
}
.rule-name { font-weight: 500; font-size: 13px; color: #1E293B; flex: 1; }
.rule-reason { font-size: 12px; color: #94A3B8; }
```

- [ ] **Step 3: Dashboard 增加风控告警卡片**

In `DashboardView.vue`, add after the charts row:

```html
<el-row :gutter="20" style="margin-top:20px">
  <el-col :span="24">
    <el-card class="chart-card">
      <template #header>
        <div class="chart-header">
          <span>风控告警（近7天）</span>
          <el-tag size="small" type="warning">Drools 11 规则</el-tag>
        </div>
      </template>
      <el-table :data="riskAlerts" size="small" class="data-table">
        <el-table-column label="" width="32">
          <template #default="{ row }">
            <el-icon :color="row.action === 'BLOCK' ? '#EF4444' : '#F59E0B'" :size="16">
              <WarningFilled v-if="row.action === 'WARN'" />
              <CircleCloseFilled v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="告警描述" />
        <el-table-column prop="businessNo" label="关联单据" width="200" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="action" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.action === 'BLOCK' ? 'danger' : 'warning'" size="small">
              {{ row.action === 'BLOCK' ? '严重' : '警告' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="riskAlerts.length === 0" description="暂无风控告警" :image-size="60" />
    </el-card>
  </el-col>
</el-row>
```

In script, add:

```typescript
import { ref } from 'vue'
import { getRecentAlerts } from '../../api/ai'

const riskAlerts = ref<any[]>([])

// In onMounted, add:
getRecentAlerts({ days: 7, limit: 5 }).then(res => {
  riskAlerts.value = res.data ?? []
}).catch(() => {})
```

- [ ] **Step 4: 在 ai.ts 中添加风控告警 API**

```typescript
export function getRecentAlerts(params: { days: number; limit: number }) {
  return request.get('/ai/review/alerts/recent', { params })
}
```

- [ ] **Step 5: Commit**

```bash
git add expense-web/src/views/ai/AIReviewView.vue expense-web/src/views/dashboard/DashboardView.vue expense-web/src/api/ai.ts approval-service/src/main/java/com/expenseflow/approval/controller/ApprovalTaskController.java
git commit -m "feat: add rule results display in AI review + risk alerts card in dashboard"
```

---

### Task 14: 全栈集成验证

- [ ] **Step 1: 启动后端确认编译通过**

```bash
cd D:/RecoginitionOCR
mvn compile -DskipTests -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 前端编译确认无错误**

```bash
cd D:/RecoginitionOCR/expense-web
npx vue-tsc --noEmit 2>&1 | tail -20
```
Expected: no TypeScript errors (or only pre-existing ones)

- [ ] **Step 3: 启动全部服务做冒烟测试**

```bash
cd D:/RecoginitionOCR
docker compose up -d
# Wait for infrastructure
mvn spring-boot:run -pl expense-service &
# ... start all services
```

- [ ] **Step 4: 验证关键页面**
  - 登录 admin → 侧边栏出现"系统管理"+"财务管理"
  - 用户管理：列表/新建/编辑/删除/重置密码/启用禁用
  - 角色管理：列表/新建/编辑/分配权限（权限树）
  - 部门管理：树形 + 点击查看员工
  - 差旅标准：列表/新建/编辑/删除
  - 租户管理：列表/新建/编辑/启停
  - 打款管理：列表 + 执行打款（校验预算）
  - 预算管理：列表 + 使用进度条 + 新建/编辑/删除
  - Dashboard：风控告警卡片
  - AI 审单：规则检查明细

- [ ] **Step 5: Commit final verification**

```bash
git add -A
git commit -m "chore: final integration verification and cleanup"
```
