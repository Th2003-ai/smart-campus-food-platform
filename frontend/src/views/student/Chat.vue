<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>智能客服</span>
          <el-button text type="primary" :disabled="!sessionId" @click="onClear">清空会话</el-button>
        </div>
      </template>

      <div ref="listRef" class="messages">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <div class="text">{{ m.text }}</div>
            <div v-if="m.knowledge?.length" class="knowledge text-muted">
              参考知识：{{ m.knowledge.join('；') }}
            </div>
            <div v-if="m.needTicket" class="ticket text-muted">
              已为你转人工客服，可在「个人中心 → 我的工单」查看处理进度
            </div>
            <div v-if="m.fallback" class="fallback text-muted">AI 服务暂不可用，已用知识库回答</div>
          </div>
        </div>
        <div v-if="loading" class="msg ai">
          <div class="bubble thinking">正在思考…</div>
        </div>
      </div>

      <div class="quick">
        <el-button
          v-for="q in quickQuestions"
          :key="q"
          size="small"
          text
          type="primary"
          @click="onQuick(q)"
        >
          {{ q }}
        </el-button>
      </div>

      <div class="input-row">
        <el-input v-model="message" placeholder="例如：我的订单怎么还没送到？" @keyup.enter="onSend" />
        <el-button type="primary" :loading="loading" @click="onSend">发送</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'

import { chat, clearChatContext } from '@/api/ai'

interface ChatMessage {
  role: 'user' | 'ai'
  text: string
  knowledge?: string[]
  needTicket?: boolean
  fallback?: boolean
}

const message = ref('')
const loading = ref(false)
const sessionId = ref('')
const listRef = ref<HTMLElement>()

const quickQuestions = ['怎么退款？', '订单多久能送到？', '如何修改收货地址？']

const messages = ref<ChatMessage[]>([
  { role: 'ai', text: '你好，我是校园外卖智能客服。可以问订单、退款、配送等问题。' }
])

async function scrollBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

async function onSend() {
  const text = message.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  message.value = ''
  loading.value = true
  await scrollBottom()
  try {
    const res = await chat({ message: text, sessionId: sessionId.value || undefined })
    // 多轮上下文：必须回传 sessionId，后端用它从 Redis 取最近若干轮对话
    sessionId.value = res.sessionId || sessionId.value
    messages.value.push({
      role: 'ai',
      text: res.answer || '抱歉，我没有理解你的问题',
      knowledge: (res.knowledge as string[]) || [],
      needTicket: !!res.needTicket,
      fallback: !!res.fallback
    })
  } catch {
    messages.value.push({ role: 'ai', text: '客服服务暂时不可用，请稍后再试' })
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

function onQuick(q: string) {
  message.value = q
  onSend()
}

async function onClear() {
  try {
    await clearChatContext(sessionId.value)
  } catch {
    // 清空失败不影响前端重置
  }
  sessionId.value = ''
  messages.value = [{ role: 'ai', text: '会话已清空，有什么可以帮你？' }]
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.messages {
  max-height: 460px;
  min-height: 220px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px;
}
.msg {
  display: flex;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 76%;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px 14px;
}
.msg.user .bubble {
  background: #ecf5ff;
}
.text {
  line-height: 1.7;
  white-space: pre-wrap;
}
.thinking {
  color: #909399;
}
.knowledge,
.ticket,
.fallback {
  margin-top: 6px;
  font-size: 12px;
}
.ticket {
  color: #e6a23c;
}
.quick {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin: 12px 0 8px;
}
.input-row {
  display: flex;
  gap: 10px;
}
.text-muted {
  color: #909399;
}
</style>
