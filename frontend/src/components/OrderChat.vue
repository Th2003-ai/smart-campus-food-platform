<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="400px"
    @open="onOpen"
    @closed="onClose"
  >
    <div ref="listRef" class="chat-list">
      <el-empty v-if="!messages.length" description="还没有消息，发一句试试" :image-size="60" />
      <div
        v-for="item in messages"
        :key="item.id"
        class="chat-row"
        :class="{ mine: item.fromUserId === myUserId }"
      >
        <div class="bubble">{{ item.content }}</div>
        <div class="meta">{{ roleText(item.fromRole) }} · {{ timeText(item.createTime) }}</div>
      </div>
    </div>

    <template #footer>
      <div class="chat-input">
        <el-input
          v-model="content"
          maxlength="500"
          placeholder="输入消息，回车发送"
          @keyup.enter="onSend"
        />
        <el-button type="primary" :loading="sending" @click="onSend">发送</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { listMessages, sendMessage, type ChatMessage } from '@/api/chat'
import { OrderSocket } from '@/utils/websocket'

const props = withDefaults(defineProps<{ orderId: string; title?: string }>(), {
  title: '订单沟通'
})

const visible = defineModel<boolean>({ required: true })

/** 当前登录人：登录时写入，用来把「我的消息」靠右显示 */
const USER_ID_KEY = 'CAMPUS_USER_ID'
const myUserId = localStorage.getItem(USER_ID_KEY) || ''

const messages = ref<ChatMessage[]>([])
const content = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)
let socket: OrderSocket | null = null

const ROLE_TEXT: Record<string, string> = {
  STUDENT: '学生',
  MERCHANT: '商户',
  RIDER: '骑手',
  ADMIN: '管理员'
}

function roleText(role: string) {
  return ROLE_TEXT[role] || role
}

function timeText(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').slice(5, 16)
}

async function load() {
  if (!props.orderId) return
  try {
    messages.value = await listMessages(props.orderId)
    scrollToBottom()
  } catch {
    // 接口未登录或订单不归自己时后端会返回 403，这里静默即可
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function onOpen() {
  messages.value = []
  content.value = ''
  load()
  // 复用布局已建好的连接没必要，这里独立开一条，服务端按 userId 归组，多连接互不影响
  socket = new OrderSocket()
  socket.on('CHAT', (data: ChatMessage) => {
    if (data && data.orderId === props.orderId) {
      messages.value.push(data)
      scrollToBottom()
    }
  })
  socket.on('CHAT_ERROR', (data: { message?: string }) => {
    ElMessage.warning(data?.message || '消息发送失败')
  })
  socket.connect()
}

function onClose() {
  socket?.close()
  socket = null
}

async function onSend() {
  const text = content.value.trim()
  if (!text) return
  if (text.length > 500) {
    ElMessage.warning('消息不能超过 500 字')
    return
  }
  sending.value = true
  try {
    const sentInRealtime = socket?.send('CHAT', { orderId: props.orderId, content: text })
    if (!sentInRealtime) {
      // WebSocket 未连上时退回 REST，保证消息不丢
      const saved = await sendMessage(props.orderId, text)
      messages.value.push(saved)
      scrollToBottom()
    }
    content.value = ''
  } catch {
    ElMessage.error('消息发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

watch(
  () => props.orderId,
  () => {
    if (visible.value) load()
  }
)
</script>

<style scoped>
.chat-list {
  height: 100%;
  overflow-y: auto;
  padding: 4px 2px;
}

.chat-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 14px;
}

.chat-row.mine {
  align-items: flex-end;
}

.bubble {
  max-width: 78%;
  padding: 8px 12px;
  border-radius: 10px;
  background: #f2f4f7;
  color: #1f2d3d;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-row.mine .bubble {
  background: #e8f1ff;
}

.meta {
  margin-top: 4px;
  color: #9aa4b2;
  font-size: 12px;
}

.chat-input {
  display: flex;
  gap: 8px;
}
</style>