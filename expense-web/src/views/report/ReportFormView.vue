<template>
  <div class="page">
    <el-card>
      <template #header><span>{{ isEdit ? '编辑报销单' : '新建报销单' }}</span></template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12"><el-form-item label="报销日期"><el-input v-model="form.reportDate" type="date" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <el-divider>报销明细 (总额: {{ totalAmount }})</el-divider>
      <el-button type="primary" size="small" @click="showItemDialog=true; itemForm={}" style="margin-bottom:12px">添加明细</el-button>
      <el-table :data="items" stripe>
        <el-table-column prop="expenseType" label="费用类型" width="120" />
        <el-table-column prop="expenseDate" label="日期" width="120" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作" width="80">
          <template #default="{ $index }"><el-button type="danger" size="small" @click="items.splice($index,1)" :disabled="form.status!=='DRAFT'">删除</el-button></template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px">
        <el-button type="primary" @click="handleSave" :loading="loading">保存</el-button>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-permission="'report:submit'" type="success" @click="handleSubmit" :loading="loading" v-if="isEdit&&form.status==='DRAFT'">提交审批</el-button>
        <el-button type="warning" @click="handlePay" :loading="loading" v-if="form.status==='APPROVED'">打款</el-button>
      </div>
    </el-card>

    <el-dialog v-model="showItemDialog" title="添加明细" width="400px">
      <el-form :model="itemForm" label-width="80px">
        <el-form-item label="费用类型"><el-select v-model="itemForm.expenseType" style="width:100%"><el-option v-for="t in ['TRANSPORT','HOTEL','MEAL','OTHER']" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="日期"><el-input v-model="itemForm.expenseDate" type="date" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="itemForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="itemForm.description" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showItemDialog=false">取消</el-button><el-button type="primary" @click="addItem">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createReport, updateReport, getReportDetail, submitReport, addReportItem, payReport } from '../../api/report'
import { ElMessage } from 'element-plus'

const route = useRoute(); const router = useRouter()
const isEdit = ref(!!route.params.id)
const loading = ref(false)
const showItemDialog = ref(false)
const form = reactive<any>({ reportDate: '', remark: '', status: 'DRAFT' })
const items = ref<any[]>([])
const itemForm = reactive<any>({ expenseType: 'TRANSPORT', expenseDate: '', amount: 0, description: '' })

const totalAmount = computed(() => items.value.reduce((s: number, i: any) => s + (i.amount||0), 0))

onMounted(async () => {
  if (isEdit.value) { const res = await getReportDetail(Number(route.params.id)); Object.assign(form, res.data); items.value = res.data.items || [] }
})

async function handleSave() {
  loading.value = true
  try {
    if (isEdit.value) { await updateReport(Number(route.params.id), form) }
    else { const res = await createReport(form); router.replace(`/report/${res.data.id}/edit`); isEdit.value = true }
    // Sync items
    for (const item of items.value) {
      if (!item.id) { await addReportItem(Number(route.params.id||form.id), item) }
    }
    ElMessage.success('保存成功')
  } finally { loading.value = false }
}
async function handleSubmit() { loading.value = true; try { await submitReport(Number(route.params.id)); ElMessage.success('提交成功'); router.push('/report') } finally { loading.value = false } }
async function handlePay() { loading.value = true; try { await payReport(Number(route.params.id)); ElMessage.success('打款成功'); router.push('/report') } finally { loading.value = false } }
function addItem() { items.value.push({ ...itemForm }); showItemDialog.value = false }
</script>

<style scoped>.page { padding: 0; }</style>
