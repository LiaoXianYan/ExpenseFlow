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
