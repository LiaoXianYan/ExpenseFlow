<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Cpu /></el-icon>AI 审单</h2>
        <p class="subtitle">DeepSeek 智能审核报销单，辅助风险识别</p>
      </div>
    </div>

    <el-card class="form-card">
      <el-form :model="form" label-position="top" class="review-form">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="业务类型">
              <el-select v-model="form.businessType" size="large"><el-option value="EXPENSE_REPORT" label="报销单" /><el-option value="TRAVEL_REQUEST" label="出差申请" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务 ID"><el-input-number v-model="form.businessId" :min="1" size="large" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总金额"><el-input-number v-model="form.totalAmount" :min="0" :precision="2" size="large" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">费用明细</el-divider>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-select v-model="itemForm.expenseType" placeholder="类型"><el-option v-for="t in ['TRANSPORT','HOTEL','MEAL','OTHER']" :key="t" :label="t" :value="t" /></el-select>
          </el-col>
          <el-col :span="6">
            <el-input-number v-model="itemForm.amount" :min="0" placeholder="金额" style="width:100%" />
          </el-col>
          <el-col :span="8">
            <el-input v-model="itemForm.description" placeholder="说明（选填）" />
          </el-col>
          <el-col :span="4">
            <el-button @click="addItem" style="width:100%">+ 添加</el-button>
          </el-col>
        </el-row>

        <div v-if="form.items.length>0" style="margin-top:12px">
          <el-tag v-for="(item,i) in form.items" :key="i" closable @close="form.items.splice(i,1)" style="margin:4px" size="default">{{ item.expenseType }}: &yen;{{ item.amount }}元</el-tag>
        </div>

        <el-button type="primary" size="large" @click="handleReview" :loading="loading" style="margin-top:20px;width:200px">
          <el-icon style="margin-right:4px"><Cpu /></el-icon>开始 AI 审单
        </el-button>
      </el-form>

      <div v-if="result" class="result-area">
        <el-divider />
        <el-descriptions :column="2" border>
          <el-descriptions-item label="审单结果">
            <el-tag :type="result.reviewResult==='APPROVED'?'success':result.reviewResult==='REJECTED'?'danger':'warning'" size="large">{{ result.reviewResult }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="result.riskLevel==='HIGH'?'danger':result.riskLevel==='MEDIUM'?'warning':'success'" size="large">{{ result.riskLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="置信度">{{ (result.confidence*100).toFixed(0) }}%</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ result.processTimeMs }}ms</el-descriptions-item>
        </el-descriptions>
        <el-alert :title="result.reviewOpinion" type="info" :closable="false" style="margin-top:12px;border-radius:10px" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { reviewExpense } from '../../api/ai'

const loading = ref(false); const result = ref<any>(null)
const form = reactive<any>({ businessType: 'EXPENSE_REPORT', businessId: 1, totalAmount: 0, items: [] })
const itemForm = reactive({ expenseType: 'TRANSPORT', amount: 0, description: '' })

function addItem() { form.items.push({ ...itemForm }); form.totalAmount = form.items.reduce((s: number, i: any) => s + i.amount, 0) }

async function handleReview() {
  loading.value = true
  try { const res = await reviewExpense(form); result.value = res.data } finally { loading.value = false }
}
</script>

<style scoped>
.page { max-width: 1000px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }
.form-card { border-radius: 14px; }
.result-area { margin-top: 8px; }
</style>
