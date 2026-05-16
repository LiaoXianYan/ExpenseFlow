<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Bell /></el-icon>消息中心</h2>
        <p class="subtitle">站内消息与钉钉通知</p>
      </div>
      <el-button type="primary" plain size="default" @click="handleMarkAll" :disabled="tableData.length===0">
        全部已读
      </el-button>
    </div>

    <el-card class="table-card">
      <el-table :data="tableData" stripe v-loading="loading" class="data-table">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="title" label="标题" width="220" />
        <el-table-column prop="content" label="内容" min-width="300" />
        <el-table-column prop="isRead" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isRead?'info':'warning'" effect="plain" size="small">{{ row.isRead?'已读':'未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleMarkRead(row)" v-if="!row.isRead">标记已读</el-button>
            <span v-else style="color:#94a3b8;font-size:13px">已读</span>
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
import { getMessageList, markRead, markAllRead } from '../../api/notification'
import { ElMessage } from 'element-plus'

const tableData = ref([]); const loading = ref(false); const page = ref(1); const total = ref(0)

async function loadData() {
  loading.value = true
  try { const res = await getMessageList({ page: page.value, size: 10 }); tableData.value = res.data.records; total.value = res.data.total } finally { loading.value = false }
}
loadData()

async function handleMarkRead(row: any) { await markRead(row.id); loadData() }
async function handleMarkAll() { await markAllRead(); ElMessage.success('全部已读'); loadData() }
</script>

<style scoped>
.page {  }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }
.table-card { border-radius: 14px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
