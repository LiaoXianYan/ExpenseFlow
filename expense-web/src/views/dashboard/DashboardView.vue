<template>
  <div class="dashboard">
    <!-- Welcome -->
    <div class="welcome-card">
      <div class="welcome-text">
        <h2>{{ greeting }}</h2>
        <p>差旅报销智能管理平台 — 今日概览</p>
      </div>
      <div class="welcome-date">{{ today }}</div>
    </div>

    <!-- Stat Cards -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <div class="stat-card stat-blue">
          <div class="stat-icon">
            <el-icon :size="28"><Promotion /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.travel }}</div>
            <div class="stat-label">出差申请</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-green">
          <div class="stat-icon">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.report }}</div>
            <div class="stat-label">报销单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-orange">
          <div class="stat-icon">
            <el-icon :size="28"><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pending }}</div>
            <div class="stat-label">待审批</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-purple">
          <div class="stat-icon">
            <el-icon :size="28"><Wallet /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">&yen;{{ stats.payment }}</div>
            <div class="stat-label">本月打款</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- Charts -->
    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header"><span>费用分布</span><el-tag size="small" type="info">本月</el-tag></div>
          </template>
          <div ref="pieChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="chart-header"><span>审批统计</span><el-tag size="small" type="info">近30天</el-tag></div>
          </template>
          <div ref="barChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getTravelList } from '../../api/travel'
import { getReportList } from '../../api/report'
import { getTaskList } from '../../api/approval'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const stats = reactive({ travel: 0, report: 0, pending: 0, payment: '0' })
const pieChart = ref(); const barChart = ref()

const today = new Date().toLocaleDateString('zh-CN', { year:'numeric', month:'long', day:'numeric', weekday:'long' })
const greeting = (() => {
  const h = new Date().getHours()
  const name = userStore.userInfo?.realName || '管理员'
  if (h < 12) return `早上好，${name}`
  if (h < 18) return `下午好，${name}`
  return `晚上好，${name}`
})()

onMounted(async () => {
  try {
    const [tRes, rRes, aRes] = await Promise.all([
      getTravelList({ page: 1, size: 1 }),
      getReportList({ page: 1, size: 1 }),
      getTaskList()
    ])
    stats.travel = tRes.data.total; stats.report = rRes.data.total; stats.pending = (aRes.data||[]).length

    const pie = echarts.init(pieChart.value)
    pie.setOption({
      color: ['#3B82F6','#10B981','#F59E0B','#8B5CF6'],
      tooltip: { trigger: 'item' },
      series: [{ type: 'pie', radius: ['50%','78%'], center: ['50%','55%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 3 },
        label: { show: true, formatter: '{b}\n{d}%', fontSize: 12 },
        data: [{ value: 40, name: '交通' },{ value: 25, name: '住宿' },{ value: 20, name: '餐费' },{ value: 15, name: '其他' }] }]
    })

    const bar = echarts.init(barChart.value)
    bar.setOption({
      color: ['#3B82F6'],
      tooltip: { trigger: 'axis' },
      grid: { top: 10, left: 0, right: 20, bottom: 0, containLabel: true },
      xAxis: { data: ['已通过','审批中','已驳回','已撤回'], axisLabel: { color: '#64748b' }, axisLine: { show: false }, axisTick: { show: false } },
      yAxis: { splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { show: false } },
      series: [{ type: 'bar', data: [12, 5, 2, 1], barWidth: 32, itemStyle: { borderRadius: [6,6,0,0] } }]
    })
  } catch(e) {}
})
</script>

<style scoped>
.dashboard { }

.welcome-card {
  background: linear-gradient(135deg, #1E3A5F 0%, #3B82F6 100%);
  border-radius: 16px; padding: 28px 32px; color: #fff;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 24px; box-shadow: 0 8px 32px rgba(30,58,95,0.2);
}

.welcome-card h2 { font-size: 22px; font-weight: 700; margin: 0 0 4px; }
.welcome-card p { font-size: 13px; opacity: 0.8; margin: 0; }
.welcome-date { font-size: 13px; opacity: 0.75; }

.stats-row { margin-bottom: 0; }

.stat-card {
  background: #fff; border-radius: 14px; padding: 22px 20px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  transition: all 0.2s; cursor: pointer;
}

.stat-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.08); }

.stat-icon {
  width: 56px; height: 56px; border-radius: 14px; display: flex;
  align-items: center; justify-content: center; color: #fff;
}

.stat-blue .stat-icon { background: linear-gradient(135deg,#3B82F6,#60A5FA); }
.stat-green .stat-icon { background: linear-gradient(135deg,#10B981,#34D399); }
.stat-orange .stat-icon { background: linear-gradient(135deg,#F59E0B,#FBBF24); }
.stat-purple .stat-icon { background: linear-gradient(135deg,#8B5CF6,#A78BFA); }

.stat-value { font-size: 26px; font-weight: 700; color: #1E293B; line-height: 1.2; }
.stat-label { font-size: 13px; color: #94A3B8; margin-top: 2px; }

.chart-card { border-radius: 14px; }

.chart-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: #1E3A5F; }
</style>
