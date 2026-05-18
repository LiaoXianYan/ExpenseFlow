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
