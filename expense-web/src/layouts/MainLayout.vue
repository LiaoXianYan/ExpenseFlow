<template>
  <el-container class="main-layout">
    <el-aside width="220px" class="aside">
      <div class="logo">ExpenseFlow</div>
      <el-menu :default-active="route.path" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/dashboard"><el-icon><DataAnalysis /></el-icon>工作台</el-menu-item>
        <el-sub-menu index="travel">
          <template #title><el-icon><Promotion /></el-icon>出差申请</template>
          <el-menu-item index="/travel">申请列表</el-menu-item>
          <el-menu-item index="/travel/create">新建申请</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="report">
          <template #title><el-icon><Document /></el-icon>报销管理</template>
          <el-menu-item index="/report">报销列表</el-menu-item>
          <el-menu-item index="/report/create">新建报销</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/invoice"><el-icon><Picture /></el-icon>发票管理</el-menu-item>
        <el-menu-item index="/approval"><el-icon><Checked /></el-icon>审批工作台</el-menu-item>
        <el-sub-menu index="ai">
          <template #title><el-icon><Cpu /></el-icon>AI 能力</template>
          <el-menu-item index="/ai-review">AI 审单</el-menu-item>
          <el-menu-item index="/ai-assistant">AI 助手</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/notification"><el-icon><Bell /></el-icon>消息通知</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title">差旅报销智能管理平台</span>
        <div class="header-right">
          <span>{{ userStore.userInfo?.realName || '管理员' }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.main-layout { height: 100vh; }
.aside { background: #304156; overflow-y: auto; }
.logo { color: #fff; text-align: center; padding: 16px 0; font-size: 20px; font-weight: bold; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #e6e6e6; }
.header .title { font-size: 16px; font-weight: 500; }
.header-right { display: flex; align-items: center; gap: 12px; }
.main { background: #f0f2f5; min-height: 0; }
</style>
