<template>
  <div class="page">
    <el-card>
      <template #header>发票管理</template>
      <el-upload :http-request="handleUpload" accept=".png,.jpg,.jpeg,.pdf" :limit="1">
        <el-button type="primary">上传发票</el-button>
        <template #tip><div class="tip">支持 PNG/JPG/PDF，最大 10MB</div></template>
      </el-upload>

      <el-table :data="tableData" stripe v-loading="loading" style="margin-top:16px">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="invoiceNo" label="发票号" width="160" />
        <el-table-column prop="totalAmount" label="金额" width="100" />
        <el-table-column prop="sellerName" label="销售方" />
        <el-table-column prop="ocrStatus" label="OCR" width="100">
          <template #default="{ row }"><el-tag :type="row.ocrStatus==='SUCCESS'?'success':row.ocrStatus==='FAILED'?'danger':'warning'">{{ row.ocrStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="ocrConfidence" label="置信度" width="90" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="handleOcr(row)">OCR 识别</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="prev,pager,next,total" @change="loadData" style="margin-top:16px;justify-content:flex-end" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { uploadInvoice, getInvoiceList, triggerOcr, getInvoiceDetail } from '../../api/invoice'
import { triggerOcrRecognition } from '../../api/ai'
import { ElMessage } from 'element-plus'

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

<style scoped>.page { padding: 0; } .tip { color: #999; font-size: 12px; margin-top: 4px; }</style>
