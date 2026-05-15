<template>
  <div class="page">
    <el-card>
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width:140px" @change="loadData">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="$router.push('/report/create')">新建报销单</el-button>
      </div>
      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="reportNo" label="报销编号" width="180" />
        <el-table-column prop="totalAmount" label="总金额" width="110" />
        <el-table-column prop="reportDate" label="报销日期" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="items" label="明细数" width="80">
          <template #default="{ row }">{{ row.items?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/report/${row.id}/edit`)" v-if="row.status==='DRAFT'">编辑</el-button>
            <el-button size="small" type="success" @click="handleSubmit(row)" v-if="row.status==='DRAFT'">提交</el-button>
            <el-button size="small" type="primary" @click="handlePay(row)" v-if="row.status==='APPROVED'">打款</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" v-if="row.status==='DRAFT'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next,total" @change="loadData" style="margin-top:16px;justify-content:flex-end" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getReportList, submitReport, deleteReport, payReport } from '../../api/report'
import { ElMessage, ElMessageBox } from 'element-plus'

const statuses = ['DRAFT','SUBMITTED','APPROVING','APPROVED','REJECTED','PAID']
const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const statusFilter = ref('')

function statusTag(s: string) { const m: any = { DRAFT:'info',SUBMITTED:'warning',APPROVING:'',APPROVED:'success',REJECTED:'danger',PAID:'success' }; return m[s]||'info' }

async function loadData() {
  loading.value = true
  try {
    const res = await getReportList({ page: page.value, size: 10, status: statusFilter.value || undefined })
    tableData.value = res.data.records; total.value = res.data.total
  } finally { loading.value = false }
}
loadData()

async function handleSubmit(row: any) { await ElMessageBox.confirm('确认提交？'); await submitReport(row.id); ElMessage.success('已提交'); loadData() }
async function handlePay(row: any) { await ElMessageBox.confirm('确认打款？'); await payReport(row.id); ElMessage.success('打款成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确认删除？'); await deleteReport(row.id); ElMessage.success('已删除'); loadData() }
</script>

<style scoped>.page { padding: 0; } .toolbar { display: flex; justify-content: space-between; margin-bottom: 16px; }</style>
