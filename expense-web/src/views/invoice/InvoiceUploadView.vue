<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Picture /></el-icon>票据管理</h2>
        <p class="subtitle">上传票据并自动 OCR 识别</p>
      </div>
      <el-upload :http-request="handleUpload" accept=".png,.jpg,.jpeg,.pdf" :limit="1">
        <el-button type="primary" size="large"><el-icon style="margin-right:4px"><Upload /></el-icon>上传发票</el-button>
        <template #tip><div class="upload-tip">支持 PNG / JPG / PDF，最大 10MB</div></template>
      </el-upload>
    </div>

    <el-card class="table-card">
      <el-table :data="tableData" stripe v-loading="loading" class="data-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="invoiceNo" label="发票号" width="170" />
        <el-table-column prop="totalAmount" label="金额" width="110">
          <template #default="{ row }"><span class="amount">&yen;{{ row.totalAmount }}</span></template>
        </el-table-column>
        <el-table-column prop="sellerName" label="销售方" min-width="180" />
        <el-table-column prop="ocrStatus" label="OCR" width="100">
          <template #default="{ row }">
            <el-tag :type="row.ocrStatus==='SUCCESS'?'success':row.ocrStatus==='FAILED'?'danger':'warning'" effect="plain">{{ row.ocrStatus || 'PENDING' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ocrConfidence" label="置信度" width="90" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-permission="'ocr:recognize'" size="small" type="primary" @click="handleOcr(row)">OCR 识别</el-button>
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
import { uploadInvoice, getInvoiceList } from '../../api/invoice'
import { triggerOcrRecognition } from '../../api/ai'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'

const tableData = ref([]); const loading = ref(false); const page = ref(1); const total = ref(0)

async function loadData() {
  loading.value = true
  try { const res = await getInvoiceList({ page: page.value, size: 10 }); tableData.value = res.data.records; total.value = res.data.total } finally { loading.value = false }
}
loadData()

async function handleUpload(options: any) {
  const fd = new FormData(); fd.append('file', options.file)
  try { await uploadInvoice(fd); ElMessage.success('上传成功'); loadData() } catch(e) {}
}
async function handleOcr(row: any) {
  try { await triggerOcrRecognition(row.id, row.imageUrl); ElMessage.success('OCR 已触发'); setTimeout(loadData, 2000) } catch(e) {}
}
</script>

<style scoped>
.page {  }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }
.table-card { border-radius: 14px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
.amount { font-weight: 600; color: #1E3A5F; }
.upload-tip { color: #94a3b8; font-size: 12px; margin-top: 6px; }
</style>
