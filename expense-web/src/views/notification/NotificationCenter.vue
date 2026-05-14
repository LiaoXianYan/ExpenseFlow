<template>
  <div class="page">
    <el-card>
      <template #header>
        <span>消息通知</span>
        <el-button type="primary" size="small" style="float:right" @click="handleMarkAll" :disabled="tableData.length===0">全部已读</el-button>
      </template>
      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="title" label="标题" width="200" />
        <el-table-column prop="content" label="内容" />
        <el-table-column prop="isRead" label="状态" width="80">
          <template #default="{ row }"><el-tag :type="row.isRead?'info':'warning'">{{ row.isRead?'已读':'未读' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" @click="handleMarkRead(row)" v-if="!row.isRead">标记已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next,total" @change="loadData" style="margin-top:16px;justify-content:flex-end" />
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

<style scoped>.page { padding: 0; }</style>
