<template>
  <div class="page">
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6"><el-statistic title="出差申请" :value="stats.travel" /></el-col>
      <el-col :span="6"><el-statistic title="报销单" :value="stats.report" /></el-col>
      <el-col :span="6"><el-statistic title="待审批" :value="stats.pending" /></el-col>
      <el-col :span="6"><el-statistic title="本月打款" :value="stats.payment" prefix="¥" /></el-col>
    </el-row>
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12"><el-card><template #header>费用分布</template><div ref="pieChart" style="height:300px" /></el-card></el-col>
      <el-col :span="12"><el-card><template #header>审批统计</template><div ref="barChart" style="height:300px" /></el-card></el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getTravelList } from '../../api/travel'
import { getReportList } from '../../api/report'
import { getTaskList } from '../../api/approval'

const stats = reactive({ travel: 0, report: 0, pending: 0, payment: '0' })
const pieChart = ref(); const barChart = ref()

onMounted(async () => {
  try {
    const [tRes, rRes, aRes] = await Promise.all([
      getTravelList({ page: 1, size: 1 }), getReportList({ page: 1, size: 1 }), getTaskList()
    ])
    stats.travel = tRes.data.total; stats.report = rRes.data.total; stats.pending = (aRes.data||[]).length

    // Pie chart
    const pie = echarts.init(pieChart.value)
    pie.setOption({
      tooltip: { trigger: 'item' },
      series: [{ type: 'pie', radius: ['40%','70%'], data: [{ value: 40, name: '交通' },{ value: 25, name: '住宿' },{ value: 20, name: '餐费' },{ value: 15, name: '其他' }] }]
    })

    // Bar chart
    const bar = echarts.init(barChart.value)
    bar.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { data: ['已通过','审批中','已驳回','已撤回'] },
      yAxis: {},
      series: [{ type: 'bar', data: [12, 5, 2, 1] }]
    })
  } catch(e) {}
})
</script>

<style scoped>.page { padding: 0; } .stats-row { text-align: center; }</style>
