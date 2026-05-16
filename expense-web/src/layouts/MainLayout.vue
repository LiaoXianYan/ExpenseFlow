<template>
  <el-container class="main-layout">
    <!-- Sidebar -->
    <el-aside width="240px" class="aside">
      <div class="brand">
        <svg class="brand-logo" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="40" height="40" rx="10" fill="url(#brand-grad)"/>
          <path d="M11 16h18v3H11zM11 21h14v3H11zM11 26h10v3H11z" fill="#fff" opacity="0.9"/>
          <defs><linearGradient id="brand-grad" x1="0" y1="0" x2="40" y2="40"><stop offset="0%" stop-color="#3B82F6"/><stop offset="100%" stop-color="#1E3A5F"/></linearGradient></defs>
        </svg>
        <span class="brand-name">ExpenseFlow</span>
      </div>

      <el-menu :default-active="route.path" router class="nav-menu">
        <el-menu-item index="/dashboard" class="nav-top-item">
          <el-icon><DataAnalysis /></el-icon><span>工作台</span>
        </el-menu-item>

        <el-sub-menu index="travel">
          <template #title><el-icon><Promotion /></el-icon><span>出差申请</span></template>
          <el-menu-item index="/travel">申请列表</el-menu-item>
          <el-menu-item index="/travel/create">新建申请</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="report">
          <template #title><el-icon><Document /></el-icon><span>报销管理</span></template>
          <el-menu-item index="/report">报销列表</el-menu-item>
          <el-menu-item index="/report/create">新建报销</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/invoice">
          <el-icon><Picture /></el-icon><span>发票管理</span>
        </el-menu-item>

        <el-menu-item index="/approval">
          <el-icon><Checked /></el-icon><span>审批工作台</span>
        </el-menu-item>

        <el-sub-menu index="ai">
          <template #title><el-icon><Cpu /></el-icon><span>AI 能力</span></template>
          <el-menu-item index="/ai-review">AI 审单</el-menu-item>
          <el-menu-item index="/ai-assistant">AI 助手</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/notification">
          <el-icon><Bell /></el-icon><span>消息通知</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="user-info">
          <el-avatar :size="32" style="background:linear-gradient(135deg,#3B82F6,#F59E0B);font-size:14px;">
            {{ (userStore.userInfo?.realName || 'Ad')[0].toUpperCase() }}
          </el-avatar>
          <span class="user-name">{{ userStore.userInfo?.realName || '管理员' }}</span>
        </div>
      </div>
    </el-aside>

    <!-- Main -->
    <el-container class="right-area">
      <el-header class="header">
        <h3 class="page-title">差旅报销智能管理平台</h3>
        <el-button text type="danger" size="small" @click="handleLogout">
          <el-icon style="margin-right:4px"><SwitchButton /></el-icon>退出
        </el-button>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { SwitchButton } from '@element-plus/icons-vue'

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

/* === Sidebar === */
.aside {
  background: linear-gradient(180deg, #0f1a2e 0%, #162544 50%, #1a3055 100%);
  display: flex; flex-direction: column;
  overflow: hidden;
  border-right: 1px solid rgba(255,255,255,0.06);
}

.brand {
  display: flex; align-items: center; gap: 10px;
  padding: 20px 20px 12px;
}

.brand-logo { width: 40px; height: 40px; flex-shrink: 0; }

.brand-name {
  font-size: 20px; font-weight: 700; color: #f1f5f9;
  letter-spacing: -0.3px;
}

/* Navigation Menu */
.nav-menu {
  flex: 1; overflow-y: auto;
  background: transparent !important; border: none !important;
  padding: 8px 8px 0;
}

.nav-menu :deep(.el-menu-item),
.nav-menu :deep(.el-sub-menu__title) {
  border-radius: 8px; margin-bottom: 2px;
  height: 44px; line-height: 44px;
  color: #94a3b8; font-size: 14px;
  transition: all 0.2s;
}

.nav-menu :deep(.el-menu-item:hover),
.nav-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255,255,255,0.06) !important; color: #e2e8f0;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #F59E0B !important; font-weight: 600;
  background: rgba(245, 158, 11, 0.1) !important;
}

.nav-top-item { margin-top: 4px; }

/* Sidebar Footer */
.sidebar-footer {
  padding: 16px 16px;
  border-top: 1px solid rgba(255,255,255,0.06);
}

.user-info {
  display: flex; align-items: center; gap: 10px;
}

.user-name {
  font-size: 13px; color: #cbd5e1; font-weight: 500;
}

/* === Header === */
.right-area { flex-direction: column; }

.header {
  background: #fff;
  display: flex; align-items: center; justify-content: space-between;
  height: 56px; padding: 0 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
  z-index: 10; position: relative;
}

.page-title {
  font-size: 15px; font-weight: 600; color: #475569; margin: 0;
}

/* === Main Content === */
.main-content {
  background: #f1f5f9;
  padding: 24px;
  overflow-y: auto;
  min-height: 0;
}

/* Page Transition */
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
