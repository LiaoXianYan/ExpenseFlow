<template>
  <div class="page">
    <el-card>
      <template #header>AI 智能助手</template>
      <div class="chat-area">
        <div v-for="(msg,i) in messages" :key="i" :class="['msg', msg.role]">
          <strong>{{ msg.role === 'user' ? '我' : 'AI' }}:</strong> {{ msg.content }}
        </div>
      </div>
      <div class="input-area">
        <el-input v-model="question" placeholder="输入差旅政策问题..." @keyup.enter="handleAsk" />
        <el-button type="primary" @click="handleAsk" :loading="loading">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { askRag } from '../../api/ai'

const question = ref(''); const loading = ref(false)
const messages = ref<{ role: string; content: string }[]>([
  { role: 'ai', content: '你好！我是差旅政策助手，可以回答关于出差申请、报销标准、费用政策等问题。' }
])

async function handleAsk() {
  if (!question.value.trim()) return
  messages.value.push({ role: 'user', content: question.value })
  loading.value = true
  try {
    const res = await askRag(question.value)
    messages.value.push({ role: 'ai', content: res.data.answer })
    question.value = ''
  } finally { loading.value = false }
}
</script>

<style scoped>
.page { padding: 0; }
.chat-area { height: 400px; overflow-y: auto; border: 1px solid #e6e6e6; padding: 12px; margin-bottom: 12px; border-radius: 4px; }
.msg { margin-bottom: 8px; line-height: 1.6; }
.msg.user { color: #409EFF; }
.msg.ai { color: #67C23A; }
.input-area { display: flex; gap: 8px; }
</style>
