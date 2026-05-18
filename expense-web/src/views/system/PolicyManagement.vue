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
