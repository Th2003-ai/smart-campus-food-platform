<template>
  <div class="qr-panel">
    <div class="qr-frame">
      <canvas ref="canvasRef" class="qr-canvas" />

      <div v-if="status === 'expired'" class="qr-overlay">
        <el-icon class="qr-overlay-icon"><RefreshRight /></el-icon>
        <span class="qr-overlay-text">二维码已失效</span>
        <el-button link type="primary" @click="refresh">点击刷新</el-button>
      </div>
      <div v-else-if="status === 'scanned'" class="qr-overlay is-light">
        <el-icon class="qr-overlay-icon"><Cellphone /></el-icon>
        <span class="qr-overlay-text">已扫码，请在手机上确认</span>
      </div>
      <div v-else-if="status === 'confirmed'" class="qr-overlay is-light">
        <el-icon class="qr-overlay-icon"><Loading /></el-icon>
        <span class="qr-overlay-text">已确认，正在登录…</span>
      </div>
    </div>

    <p class="qr-tip">请使用校园外卖 App 扫码登录</p>
    <p class="qr-sub">二维码由浏览器本地生成，不经过服务器</p>
    <p v-if="status === 'waiting'" class="qr-countdown">{{ remainSeconds }} 秒后失效 · 票号 {{ ticket }}</p>

    <div v-if="demoEnabled" class="qr-demo">
      <p class="qr-demo-title">
        <el-icon><WarnTriangleFilled /></el-icon>
        本地演示模式：后端尚未接入扫码登录
      </p>
      <p v-if="!account" class="qr-demo-warn">
        未配置演示账号：请在本机 .env.local 填写 VITE_LOGIN_QR_DEMO_ACCOUNT 与 _PASSWORD
      </p>
      <p v-else class="qr-demo-account">确认后将以演示账号 {{ account }} 登录</p>
      <div class="qr-demo-actions">
        <el-button :disabled="status !== 'waiting'" @click="simulateScan">模拟手机扫码</el-button>
        <el-button type="primary" :disabled="status !== 'scanned'" @click="simulateConfirm">模拟手机确认</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { drawQrToCanvas, encodeQr } from '@/utils/qrcode'

const props = withDefaults(
  defineProps<{
    /** 是否显示「模拟手机」按钮（仅本地 / 演示环境打开） */
    demoEnabled?: boolean
    /** 演示账号（只用于展示提示，未配置时提示去 .env.local 填写） */
    account?: string
    /** 二维码有效期（秒） */
    expireSeconds?: number
  }>(),
  { demoEnabled: false, account: '', expireSeconds: 120 }
)

const emit = defineEmits<{ (event: 'confirm'): void }>()

type QrStatus = 'waiting' | 'scanned' | 'expired' | 'confirmed'

const canvasRef = ref<HTMLCanvasElement>()
const status = ref<QrStatus>('waiting')
const remainSeconds = ref(props.expireSeconds)
const ticket = ref('')

let ticker: ReturnType<typeof setInterval> | undefined

function randomTicket(): string {
  const chars = '0123456789ABCDEF'
  let text = ''
  for (let i = 0; i < 8; i++) {
    text += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return text
}

function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = undefined
  }
}

function startTicker() {
  stopTicker()
  ticker = setInterval(() => {
    remainSeconds.value -= 1
    if (remainSeconds.value <= 0) {
      remainSeconds.value = 0
      status.value = 'expired'
      stopTicker()
    }
  }, 1000)
}

/**
 * 重新生成二维码。
 * 内容只是一段本地票据文本（扫码可看到），不指向任何后端接口，
 * 因为扫码登录的服务端轮询 / 长连接本次不做。
 */
function refresh() {
  ticket.value = randomTicket()
  const payload = `CAMPUS-QR-${ticket.value}-${Date.now().toString(36).toUpperCase()}`
  if (canvasRef.value) {
    drawQrToCanvas(canvasRef.value, encodeQr(payload), { moduleSize: 7 })
  }
  status.value = 'waiting'
  remainSeconds.value = props.expireSeconds
  startTicker()
}

/** 演示：模拟手机端扫码 */
function simulateScan() {
  if (status.value !== 'waiting') {
    return
  }
  status.value = 'scanned'
}

/** 演示：模拟手机端确认，交给父组件完成登录 */
function simulateConfirm() {
  if (status.value !== 'scanned') {
    return
  }
  status.value = 'confirmed'
  stopTicker()
  emit('confirm')
}

/** 登录失败时由父组件调用，回到可再次演示的状态 */
function reset() {
  refresh()
}

onMounted(refresh)
onBeforeUnmount(stopTicker)

defineExpose({ refresh, reset })
</script>

<style scoped>
.qr-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 4px;
}
.qr-frame {
  position: relative;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(47, 107, 255, 0.08);
}
.qr-canvas {
  display: block;
  width: 231px;
  height: 231px;
}
.qr-overlay {
  position: absolute;
  inset: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 6px;
  color: #303133;
}
.qr-overlay-icon {
  font-size: 26px;
  color: var(--campus-primary);
}
.qr-overlay-text {
  font-size: 13px;
}
.qr-overlay.is-light {
  background: rgba(255, 255, 255, 0.88);
}
.qr-tip {
  margin: 14px 0 2px;
  font-size: 13px;
  color: #303133;
}
.qr-sub {
  margin: 0;
  font-size: 12px;
  color: #a0a5ad;
}
.qr-countdown {
  margin: 6px 0 0;
  font-size: 12px;
  color: #a0a5ad;
  letter-spacing: 0.4px;
}
.qr-demo {
  width: 100%;
  margin-top: 16px;
  padding: 10px 12px;
  border: 1px dashed #d5ddf5;
  border-radius: 8px;
  background: #f7f9ff;
}
.qr-demo-title {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0 0 8px;
  font-size: 12px;
  color: #7c8aa5;
}
.qr-demo-warn {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #d97706;
}
.qr-demo-account {
  margin: 0 0 8px;
  font-size: 12px;
  color: #7c8aa5;
}
.qr-demo-actions {
  display: flex;
  gap: 8px;
}
.qr-demo-actions .el-button {
  flex: 1;
  margin: 0;
}
</style>
