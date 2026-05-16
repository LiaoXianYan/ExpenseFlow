<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Checked /></el-icon>审批工作台</h2>
        <p class="subtitle">处理待审批的出差申请与报销单</p>
      </div>
    </div>

    <el-card class="table-card">
      <el-radio-group v-model="group" @change="loadData" class="group-tabs" size="default">
        <el-radio-button value="manager">经理审批</el-radio-button>
        <el-radio-button value="finance">财务审核</el-radio-button>
        <el-radio-button value="director">总监审批</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>

      <el-table :data="tasks" stripe v-loading="loading" class="data-table">
        <el-table-column prop="taskName" label="任务" width="110" />
        <el-table-column prop="businessType" label="类型" width="140" />
        <el-table-column prop="requestNo" label="编号" width="190" />
        <el-table-column prop="applicantName" label="申请人" width="110" />
        <el-table-column label="操作" min-width="400">
          <template #default="{ row }">
            <el-button size="small" type="success" v-permission="'approval:approve'" @click="handleAction(row, 'APPROVE')">
              <el-icon style="margin-right:4px"><Select /></el-icon>通过
            </el-button>
            <el-button size="small" type="danger" v-permission="'approval:reject'" @click="handleAction(row, 'REJECT')">
              <el-icon style="margin-right:4px"><CloseBold /></el-icon>驳回
            </el-button>
            <el-button size="small" type="warning" plain v-permission="['FINANCE','SUPER_ADMIN']" @click="showDelegate(row)">
              <el-icon style="margin-right:4px"><Share /></el-icon>委派
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading&&tasks.length===0" description="暂无待审批任务" :image-size="80" />
    </el-card>

    <el-dialog v-model="showComment" title="审批意见" width="420px">
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="请输入审批意见（可选）" />
      <template #footer>
        <el-button @click="showComment=false">取消</el-button>
        <el-button type="primary" @click="confirmAction" :loading="loading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDelegateDialog" title="委派任务" width="420px">
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
import { Select, CloseBold, Share } from '@element-plus/icons-vue'

const tasks = ref([]); const loading = ref(false); const group = ref('manager')
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

<style scoped>
.page { max-width: 1200px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }
.table-card { border-radius: 14px; }
.group-tabs { margin-bottom: 20px; }
</style>
