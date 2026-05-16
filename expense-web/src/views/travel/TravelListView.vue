<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Promotion /></el-icon>出差申请</h2>
        <p class="subtitle">管理所有出差申请记录</p>
      </div>
      <el-button type="primary" size="large" @click="$router.push('/travel/create')">
        <el-icon style="margin-right:4px"><Plus /></el-icon>新建申请
      </el-button>
    </div>

    <el-card class="table-card">
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:150px" @change="loadData">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
      </div>
      <el-table :data="tableData" stripe v-loading="loading" class="data-table">
        <el-table-column prop="requestNo" label="申请编号" width="190" />
        <el-table-column prop="travelPurpose" label="出差事由" min-width="160" />
        <el-table-column prop="destination" label="目的地" width="100" />
        <el-table-column prop="startDate" label="开始日期" width="110" />
        <el-table-column prop="endDate" label="结束日期" width="110" />
        <el-table-column prop="estimatedAmount" label="预估金额" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" effect="plain">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/travel/${row.id}/edit`)" v-if="row.status==='DRAFT'||row.status==='WITHDRAWN'">编辑</el-button>
            <el-button size="small" type="success" @click="handleSubmit(row)" v-if="row.status==='DRAFT'">提交</el-button>
            <el-button size="small" type="warning" @click="handleWithdraw(row)" v-if="row.status==='APPROVING'||row.status==='SUBMITTED'">撤回</el-button>
            <el-button size="small" type="danger" v-permission="['FINANCE','SUPER_ADMIN']" @click="handleDelete(row)" v-if="row.status==='DRAFT'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next,total" @change="loadData" background />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getTravelList, submitTravel, withdrawTravel, deleteTravel } from '../../api/travel'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const statuses = ['DRAFT','SUBMITTED','APPROVING','APPROVED','REJECTED','WITHDRAWN']
const tableData = ref([]); const loading = ref(false)
const page = ref(1); const total = ref(0); const statusFilter = ref('')

function statusTag(s: string) { const m: any = { DRAFT:'info',SUBMITTED:'warning',APPROVING:'',APPROVED:'success',REJECTED:'danger',WITHDRAWN:'info' }; return m[s]||'info' }

async function loadData() {
  loading.value = true
  try { const res = await getTravelList({ page: page.value, size: 10, status: statusFilter.value || undefined }); tableData.value = res.data.records; total.value = res.data.total } finally { loading.value = false }
}
loadData()

async function handleSubmit(row: any) { await ElMessageBox.confirm('确认提交？'); await submitTravel(row.id); ElMessage.success('已提交'); loadData() }
async function handleWithdraw(row: any) { await ElMessageBox.confirm('确认撤回？'); await withdrawTravel(row.id); ElMessage.success('已撤回'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确认删除？'); await deleteTravel(row.id); ElMessage.success('已删除'); loadData() }
</script>

<style scoped>
.page {  }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }
.table-card { border-radius: 14px; }
.toolbar { margin-bottom: 16px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
