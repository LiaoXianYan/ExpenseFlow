<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>
    <div class="login-card">
      <div class="card-header">
        <div class="brand-icon">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="48" height="48" rx="12" fill="url(#g1)"/>
            <path d="M14 18h20v4H14zM14 24h16v4H14zM14 30h12v4H14z" fill="#fff" opacity="0.9"/>
            <circle cx="36" cy="32" r="6" fill="#F59E0B"/>
            <path d="M34 32l1.5 1.5 3-3" stroke="#1E3A5F" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <defs><linearGradient id="g1" x1="0" y1="0" x2="48" y2="48"><stop offset="0%" stop-color="#1E3A5F"/><stop offset="100%" stop-color="#3B82F6"/></linearGradient></defs>
          </svg>
        </div>
        <h1>ExpenseFlow</h1>
        <p>差旅报销智能管理平台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password :prefix-icon="Lock" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="card-footer">
        <span>演示账号：admin / manager / finance</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../../api/auth'
import { useUserStore } from '../../stores/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  loading.value = true
  try {
    const res = await login(form.username, form.password)
    userStore.setToken(res.data.accessToken)
    userStore.setUserInfo(res.data.user)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) { /* handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f1a2e 0%, #1a3a5c 40%, #1e4d7b 70%, #0f1a2e 100%);
  position: relative;
  overflow: hidden;
  font-family: 'IBM Plex Sans', system-ui, sans-serif;
}

.login-bg {
  position: absolute; inset: 0; pointer-events: none;
}

.bg-shape {
  position: absolute; border-radius: 50%; filter: blur(80px); opacity: 0.15;
}

.shape-1 {
  width: 600px; height: 600px; background: #3B82F6;
  top: -200px; right: -100px; animation: float1 20s infinite ease-in-out;
}

.shape-2 {
  width: 400px; height: 400px; background: #F59E0B;
  bottom: -100px; left: -80px; animation: float2 25s infinite ease-in-out;
}

.shape-3 {
  width: 300px; height: 300px; background: #8B5CF6;
  top: 50%; left: 50%; transform: translate(-50%, -50%); animation: float3 18s infinite ease-in-out;
}

@keyframes float1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-60px, 40px) scale(1.1); }
  66% { transform: translate(30px, -30px) scale(0.9); }
}
@keyframes float2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(50px, -50px) scale(1.15); }
  66% { transform: translate(-40px, 30px) scale(0.95); }
}
@keyframes float3 {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-40%, -60%) scale(1.2); }
}

.login-card {
  position: relative; z-index: 1;
  width: 420px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 32px 64px rgba(0, 0, 0, 0.25), 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
  padding: 48px 40px 36px;
  box-sizing: border-box;
}

.card-header {
  text-align: center; margin-bottom: 36px;
}

.brand-icon {
  width: 64px; height: 64px; margin: 0 auto 16px;
}

.brand-icon svg { width: 100%; height: 100%; }

.card-header h1 {
  font-size: 28px; font-weight: 700; color: #1E3A5F; margin: 0 0 6px; letter-spacing: -0.5px;
}

.card-header p {
  font-size: 14px; color: #64748b; margin: 0; letter-spacing: 0.5px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.06) inset;
  transition: box-shadow 0.2s;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #3B82F6 inset;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-btn {
  width: 100%; height: 48px; border-radius: 10px; font-size: 16px; font-weight: 600;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #1E3A5F, #3B82F6); border: none;
  box-shadow: 0 4px 16px rgba(30, 58, 95, 0.35);
  transition: all 0.3s;
}

.login-btn:hover {
  box-shadow: 0 6px 24px rgba(30, 58, 95, 0.5);
  transform: translateY(-1px);
}

.login-btn:active { transform: translateY(0); }

.card-footer {
  text-align: center; margin-top: 20px;
  font-size: 12px; color: #94a3b8;
}
</style>
