<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>AI 点餐</span>
          <el-button text type="primary" @click="onReset">重新开始</el-button>
        </div>
      </template>

      <div class="chat">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <div class="text">{{ m.text }}</div>
            <div v-if="m.conditions?.length" class="conditions">
              <el-tag v-for="c in m.conditions" :key="c" size="small" type="info">{{ c }}</el-tag>
            </div>
            <div v-if="m.fallback" class="fallback text-muted">AI 理解暂不可用，已按关键词为你推荐</div>
            <div v-if="m.dishes?.length" class="dishes">
              <div v-for="d in m.dishes" :key="d.id" class="dish-card">
                <div class="thumb">
                  <el-image :src="dishCover(d)" fit="cover" class="thumb-img">
                    <template #error><div class="thumb-placeholder">🍽</div></template>
                  </el-image>
                </div>
                <div class="dish-info">
                  <div class="dish-name">{{ d.dishName }}</div>
                  <div class="text-muted">
                    ¥{{ money(d.price) }} · 月售 {{ d.monthlySales ?? 0 }}
                  </div>
                </div>
                <div class="dish-actions">
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="(d.stock ?? 0) <= 0"
                    @click="onAdd(d)"
                  >
                    加购
                  </el-button>
                  <el-button size="small" text type="primary" @click="router.push(`/student/dish/${d.id}`)">
                    详情
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="input-row">
        <el-input
          v-model="message"
          type="textarea"
          :rows="2"
          placeholder="例如：20 块以内、不辣、要有肉；也可以补充「再来个喝的」"
          @keyup.ctrl.enter="onSend"
        />
        <el-button type="primary" :loading="loading" @click="onSend">发送</el-button>
      </div>
      <div class="hint text-muted">
        Ctrl + Enter 发送。AI 只从在售菜品中挑选，忌口与过敏原会被二次过滤。
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { aiOrder } from '@/api/ai'
import { addToCart } from '@/api/cart'
import type { Dish } from '@/api/dish'
import { useCartStore } from '@/store/cart'
import { money } from '@/utils/dict'
import { dishCover } from '@/utils/image'

interface ChatMessage {
  role: 'user' | 'ai'
  text: string
  dishes?: Dish[]
  conditions?: string[]
  fallback?: boolean
}

const router = useRouter()
const cartStore = useCartStore()

const message = ref('')
const loading = ref(false)
const sessionId = ref('')
const messages = ref<ChatMessage[]>([
  { role: 'ai', text: '你好，想吃点什么？可以直接描述口味、预算或想吃的食材。' }
])

async function onSend() {
  const text = message.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  message.value = ''
  loading.value = true
  try {
    const res = await aiOrder({ message: text, sessionId: sessionId.value || undefined, limit: 6 })
    // 多轮对话：把后端返回的 sessionId 带回去，才能形成上下文
    sessionId.value = res.sessionId || sessionId.value
    messages.value.push({
      role: 'ai',
      text: res.reply || '为你推荐以下菜品',
      dishes: (res.dishes as Dish[]) || [],
      conditions: Object.entries(res.conditions || {}).map(([k, v]) => `${k}：${v}`),
      fallback: !!res.fallback
    })
  } catch {
    messages.value.push({ role: 'ai', text: 'AI 服务暂时不可用，请稍后再试' })
  } finally {
    loading.value = false
    await nextTick()
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
  }
}

async function onAdd(dish: Dish) {
  try {
    await addToCart({ dishId: dish.id, quantity: 1 })
    ElMessage.success(`已加入购物车：${dish.dishName}`)
    await cartStore.refresh()
  } catch {
    // 同店约束等业务错误由拦截器提示
  }
}

function onReset() {
  messages.value = [{ role: 'ai', text: '你好，想吃点什么？可以直接描述口味、预算或想吃的食材。' }]
  sessionId.value = ''
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 520px;
  overflow-y: auto;
  padding: 4px;
}
.msg {
  display: flex;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 78%;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px 14px;
}
.msg.user .bubble {
  background: #ecf5ff;
}
.text {
  line-height: 1.6;
  white-space: pre-wrap;
}
.conditions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.fallback {
  margin-top: 6px;
  font-size: 12px;
}
.dishes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}
.dish-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px;
}
.thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  flex-shrink: 0;
}
.thumb-img {
  width: 100%;
  height: 100%;
}
.thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dish-info {
  flex: 1;
  min-width: 0;
}
.dish-name {
  font-weight: 600;
}
.dish-actions {
  display: flex;
  gap: 6px;
}
.input-row {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  align-items: flex-end;
}
.hint {
  margin-top: 8px;
  font-size: 12px;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
