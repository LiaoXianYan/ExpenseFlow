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
