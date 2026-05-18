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
