<template>
  <div class="page">
    <el-card>
      <template #header><span>{{ isEdit ? '编辑出差申请' : '新建出差申请' }}</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="出差事由" prop="travelPurpose"><el-input v-model="form.travelPurpose" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目的地" prop="destination"><el-input v-model="form.destination" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate"><el-date-picker v-model="form.startDate" type="date" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate"><el-date-picker v-model="form.endDate" type="date" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估金额" prop="estimatedAmount"><el-input-number v-model="form.estimatedAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="同行人员"><el-input v-model="form.companions" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" @click="handleSave" :loading="loading">保存</el-button>
          <el-button @click="$router.back()">返回</el-button>
          <el-button type="success" @click="handleSubmit" v-if="isEdit && form.status==='DRAFT'" :loading="loading">提交审批</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createTravel, updateTravel, getTravelDetail, submitTravel } from '../../api/travel'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isEdit = ref(!!route.params.id)
const loading = ref(false)
const form = reactive<any>({ travelPurpose: '', destination: '', startDate: '', endDate: '', estimatedAmount: 0, companions: '', remark: '', status: 'DRAFT' })
const rules = {
  travelPurpose: [{ required: true, message: '请输入出差事由' }],
  destination: [{ required: true, message: '请输入目的地' }],
  startDate: [{ required: true, message: '请选择开始日期' }],
  endDate: [{ required: true, message: '请选择结束日期' }]
}

onMounted(async () => {
  if (isEdit.value) {
    const res = await getTravelDetail(Number(route.params.id))
    Object.assign(form, res.data)
  }
})

async function handleSave() {
  loading.value = true
  try {
    if (isEdit.value) {
      await updateTravel(Number(route.params.id), form)
    } else {
      const res = await createTravel(form)
      router.replace(`/travel/${res.data.id}/edit`)
      isEdit.value = true
    }
    ElMessage.success('保存成功')
  } finally { loading.value = false }
}

async function handleSubmit() {
  loading.value = true
  try {
    await submitTravel(Number(route.params.id))
    ElMessage.success('提交成功')
    router.push('/travel')
  } finally { loading.value = false }
}
</script>

<style scoped>.page { padding: 0; }</style>
