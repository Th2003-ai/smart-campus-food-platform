<template>
  <el-drawer
    v-model="visible"
    :title="drawerTitle"
    size="420px"
    @open="onOpen"
    @closed="onClose"
  >
    <!-- 会话类型说明 + 客服介入入口 -->
    <div class="thread-bar">
      <el-tag v-if="serviceMode" type="warning" size="small" effect="dark">平台客服介入中</el-tag>
      <span v-else class="thread-hint">{{ hint }}</span>
      <el-button
        v-if="allowService && !serviceMode && isCustomerSide"
        size="small"
        type="warning"
        plain
        :loading="applying"
        @click="onRequestService"
      >
        申请平台客服介入
      </el-button>
    </div>

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
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { listMessages, requestService, sendMessage, serviceThread, type ChatMessage } from '@/api/chat'
import { OrderSocket } from '@/utils/websocket'

const props = withDefaults(
  defineProps<{
    /** 订单ID（不是会话ID） */
    orderId: string
    /** 会话ID：不传=订单群聊；传 dm:... = 单聊；传 svc:... = 客服会话 */
    thread?: string
    /** 默认标题 */
    title?: string
    /** 是否显示「申请平台客服介入」 */
    allowService?: boolean
    /** 会话说明 */
    hint?: string
  }>(),
  {
    title: '订单沟通',
    thread: '',
    allowService: true,
    hint: '学生、商户、骑手三方都在这个会话里'
  }
)

const visible = defineModel<boolean>({ required: true })

/** 当前登录人：登录时写入，用来把「我的消息」靠右显示 */
const USER_ID_KEY = 'CAMPUS_USER_ID'
const ROLE_KEY = 'campus_role'
const myUserId = localStorage.getItem(USER_ID_KEY) || ''
const myRole = localStorage.getItem(ROLE_KEY) || ''

const messages = ref<ChatMessage[]>([])
const content = ref('')
const sending = ref(false)
const applying = ref(false)
const serviceMode = ref(false)
const listRef = ref<HTMLElement | null>(null)
let socket: OrderSocket | null = null

/** 实际会话ID：客服模式优先，其次是父组件传的单聊/群聊 */
const threadId = computed(() =>
  serviceMode.value ? serviceThread(props.orderId) : props.thread || props.orderId
)

const drawerTitle = computed(() => (serviceMode.value ? '平台客服' : props.title))

/** 只有顾客/商户/骑手能申请客服介入，管理员进来是处理工单 */
const isCustomerSide = computed(() => myRole !== 'ADMIN' && !!myUserId)

const ROLE_TEXT: Record<string, string> = {
  STUDENT: '学生',
  MERCHANT: '商户',
  RIDER: '骑手',
  ADMIN: '客服'
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
    messages.value = await listMessages(threadId.value)
    scrollToBottom()
  } catch {
    // 不归自己时后端返回 403，这里静默
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function connect() {
  socket?.close()
  socket = new OrderSocket()
  socket.on('CHAT', (data: ChatMessage) => {
    if (data && data.orderId === threadId.value) {
      messages.value.push(data)
      scrollToBottom()
    }
  })
  socket.on('CHAT_ERROR', (data: { message?: string }) => {
    ElMessage.warning(data?.message || '消息发送失败')
  })
  socket.connect()
}

function onOpen() {
  messages.value = []
  content.value = ''
  // 父组件用 svc: 开头打开时，直接进客服模式
  serviceMode.value = (props.thread || '').startsWith('svc:')
  load()
  connect()
}

function onClose() {
  socket?.close()
  socket = null
}

async function onRequestService() {
  applying.value = true
  try {
    const res = await requestService(props.orderId)
    serviceMode.value = true
    ElMessage.success(`已提交客服介入（工单 ${res.ticketNo}），管理员马上就来`)
    await load()
    connect()
  } catch {
    // 拦截器提示
  } finally {
    applying.value = false
  }
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
    const sentInRealtime = socket?.send('CHAT', { orderId: threadId.value, content: text })
    if (!sentInRealtime) {
      // WebSocket 未连上时退回 REST，保证消息不丢
      const saved = await sendMessage(threadId.value, text)
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
  () => [props.orderId, props.thread],
  () => {
    if (visible.value) {
      serviceMode.value = (props.thread || '').startsWith('svc:')
      load()
      connect()
    }
  }
)
</script>

<style scoped>
.thread-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px 10px;
  border-bottom: 1px dashed #ebeef5;
  margin-bottom: 8px;
}
.thread-hint {
  color: #9aa4b2;
  font-size: 12px;
}
.chat-list {
  height: calc(100% - 46px);
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