<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2><el-icon style="margin-right:6px"><Cpu /></el-icon>AI 智能助手</h2>
        <p class="subtitle">基于差旅政策知识库的智能问答</p>
      </div>
    </div>

    <el-card class="chat-card">
      <div class="chat-area" ref="chatRef">
        <div v-for="(msg,i) in messages" :key="i" :class="['msg', msg.role]">
          <div class="msg-avatar">
            <el-avatar :size="32" :style="msg.role==='user'?'background:#3B82F6':'background:linear-gradient(135deg,#1E3A5F,#3B82F6)'">
              {{ msg.role==='user' ? '我' : 'AI' }}
            </el-avatar>
          </div>
          <div class="msg-bubble">{{ msg.content }}</div>
        </div>
        <div v-if="loading" class="msg ai">
          <div class="msg-avatar"><el-avatar :size="32" style="background:linear-gradient(135deg,#1E3A5F,#3B82F6)">AI</el-avatar></div>
          <div class="msg-bubble typing">思考中...</div>
        </div>
      </div>
      <div class="input-area">
        <el-input v-model="question" placeholder="输入差旅政策问题，如：住宿标准是多少？" size="large" @keyup.enter="handleAsk" />
        <el-button type="primary" size="large" @click="handleAsk" :loading="loading">
          <el-icon style="margin-right:4px"><Promotion /></el-icon>发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { askRag } from '../../api/ai'

const question = ref(''); const loading = ref(false); const chatRef = ref()
const messages = ref<{ role: string; content: string }[]>([
  { role: 'ai', content: '你好！我是差旅政策助手，基于公司制度知识库，可以回答关于出差标准、报销政策、审批流程等问题。请随时提问。' }
])

async function scrollToBottom() {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

async function handleAsk() {
  if (!question.value.trim()) return
  messages.value.push({ role: 'user', content: question.value })
  scrollToBottom()
  const q = question.value
  question.value = ''
  loading.value = true
  try {
    const res = await askRag(q)
    messages.value.push({ role: 'ai', content: res.data.answer })
  } catch (e) {
    messages.value.push({ role: 'ai', content: '抱歉，AI 服务暂时不可用，请稍后重试。' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.page {  margin: 0 auto; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 22px; font-weight: 700; color: #1E3A5F; display: flex; align-items: center; margin: 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 4px 0 0; }

.chat-card { border-radius: 14px; }

.chat-area {
  height: 440px; overflow-y: auto; padding: 8px 0;
  background: #f8fafc; border-radius: 10px; margin-bottom: 16px; padding: 16px;
}

.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { flex-shrink: 0; }

.msg-bubble {
  max-width: 75%; padding: 10px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6;
}
.msg.user .msg-bubble { background: #3B82F6; color: #fff; border-bottom-right-radius: 4px; }
.msg.ai .msg-bubble { background: #fff; color: #1e293b; border: 1px solid #e2e8f0; border-bottom-left-radius: 4px; }

.typing { color: #94a3b8; font-style: italic; }

.input-area { display: flex; gap: 10px; }
.input-area :deep(.el-input__wrapper) { border-radius: 10px; }
</style>
