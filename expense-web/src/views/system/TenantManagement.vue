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
