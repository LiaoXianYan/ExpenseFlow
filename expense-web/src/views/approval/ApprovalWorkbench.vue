<template>
  <div class="page">
    <el-card>
      <template #header>审批工作台</template>
      <el-radio-group v-model="group" @change="loadData" style="margin-bottom:12px">
        <el-radio-button value="manager">经理审批</el-radio-button>
        <el-radio-button value="finance">财务审核</el-radio-button>
        <el-radio-button value="director">总监审批</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>

      <el-table :data="tasks" stripe v-loading="loading">
        <el-table-column prop="taskName" label="任务" width="100" />
        <el-table-column prop="businessType" label="类型" width="130" />
        <el-table-column prop="requestNo" label="编号" width="180" />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column label="操作" width="400">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleAction(row, 'APPROVE')">通过</el-button>
            <el-button size="small" type="danger" @click="handleAction(row, 'REJECT')">驳回</el-button>
            <el-button size="small" type="warning" @click="showDelegate(row)">委派</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showComment" title="审批意见" width="400px">
      <el-input v-model="comment" type="textarea" placeholder="请输入审批意见" />
      <template #footer>
        <el-button @click="showComment=false">取消</el-button>
        <el-button type="primary" @click="confirmAction" :loading="loading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDelegateDialog" title="委派任务" width="400px">
      <el-input v-model="delegateUser" placeholder="输入委派用户ID" />
      <template #footer>
        <el-button @click="showDelegateDialog=false">取消</el-button>
        <el-button type="primary" @click="confirmDelegate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getTaskList, completeTask, delegateTask } from '../../api/approval'
import { ElMessage } from 'element-plus'

const tasks = ref([]); const loading = ref(false)
const group = ref('manager')
const showComment = ref(false); const showDelegateDialog = ref(false)
const comment = ref(''); const delegateUser = ref('')
let currentTask: any = null; let currentAction = ''

async function loadData() {
  loading.value = true
  try { const res = await getTaskList(group.value || undefined); tasks.value = res.data || [] } finally { loading.value = false }
}
loadData()

function handleAction(task: any, action: string) { currentTask = task; currentAction = action; showComment.value = true }
async function confirmAction() { loading.value = true; try { await completeTask(currentTask.taskId, { action: currentAction, comment: comment.value }); ElMessage.success('操作成功'); showComment.value = false; comment.value = ''; loadData() } finally { loading.value = false } }

function showDelegate(task: any) { currentTask = task; showDelegateDialog.value = true }
async function confirmDelegate() { loading.value = true; try { await delegateTask(currentTask.taskId, delegateUser.value); ElMessage.success('委派成功'); showDelegateDialog.value = false; loadData() } finally { loading.value = false } }
</script>

<style scoped>.page { padding: 0; }</style>
