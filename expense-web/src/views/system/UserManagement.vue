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
