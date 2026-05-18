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
