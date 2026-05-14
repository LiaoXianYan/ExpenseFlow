<template>
  <div class="page">
    <el-card>
      <template #header>AI 审单</template>
      <el-form :model="form" label-width="100px" inline>
        <el-form-item label="业务类型"><el-select v-model="form.businessType"><el-option value="EXPENSE_REPORT" label="报销单" /><el-option value="TRAVEL_REQUEST" label="出差申请" /></el-select></el-form-item>
        <el-form-item label="业务ID"><el-input-number v-model="form.businessId" :min="1" /></el-form-item>
        <el-form-item label="总金额"><el-input-number v-model="form.totalAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item><el-button type="primary" @click="handleReview" :loading="loading">AI 审单</el-button></el-form-item>
      </el-form>
      <el-form :model="itemForm" label-width="80px" inline>
        <el-form-item label="明细类型"><el-select v-model="itemForm.expenseType"><el-option v-for="t in ['TRANSPORT','HOTEL','MEAL','OTHER']" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="itemForm.amount" :min="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="itemForm.description" /></el-form-item>
        <el-form-item><el-button @click="addItem">添加明细</el-button></el-form-item>
      </el-form>

      <el-tag v-for="(item,i) in form.items" :key="i" closable @close="form.items.splice(i,1)" style="margin:4px">{{ item.expenseType }}: {{ item.amount }}元</el-tag>

      <el-divider />
      <div v-if="result" class="result">
        <el-descriptions border>
          <el-descriptions-item label="审单结果"><el-tag :type="result.reviewResult==='APPROVED'?'success':result.reviewResult==='REJECTED'?'danger':'warning'">{{ result.reviewResult }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="风险等级"><el-tag :type="result.riskLevel==='HIGH'?'danger':result.riskLevel==='MEDIUM'?'warning':'success'">{{ result.riskLevel }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="置信度">{{ result.confidence }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ result.processTimeMs }}ms</el-descriptions-item>
        </el-descriptions>
        <el-alert :title="result.reviewOpinion" type="info" :closable="false" style="margin-top:12px" />
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

<style scoped>.page { padding: 0; }</style>
