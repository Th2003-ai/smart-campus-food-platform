<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- ==================== 左侧品牌区 ==================== -->
      <aside class="brand-side">
        <div class="brand-top">
          <span class="brand-badge">AI</span>
          <span class="brand-name">智能 AI 校园外卖平台</span>
        </div>

        <h1 class="brand-slogan">AI 助力校园餐饮<br />新一代智慧食堂</h1>

        <div class="brand-art" aria-hidden="true">
          <span class="ring ring-1" />
          <span class="ring ring-2" />
          <span class="ring ring-3" />
          <div class="art-card">
            <div class="art-bars">
              <span v-for="(height, index) in artBars" :key="index" :style="{ height: `${height}%` }" />
            </div>
            <div class="art-lines"><span /><span /></div>
          </div>
          <span class="tile tile-1">🍜</span>
          <span class="tile tile-2">🥗</span>
          <span class="tile tile-3">🧋</span>
          <span class="tile tile-4">🍱</span>
          <span class="tile tile-5">📊</span>
          <span class="tile tile-6">🛵</span>
        </div>

        <p class="brand-foot">引擎 · 全终端 · 数据驱动</p>
      </aside>

      <!-- ==================== 右侧表单区 ==================== -->
      <section class="form-side">
        <header class="form-head">
          <h2 class="form-title">智能 AI 校园外卖平台</h2>
          <p class="form-desc">登录后按角色进入对应工作台</p>
        </header>

        <nav class="tabs" role="tablist">
          <button
            v-for="tab in TABS"
            :key="tab.key"
            type="button"
            role="tab"
            class="tab"
            :class="{ 'is-active': activeTab === tab.key }"
            :aria-selected="activeTab === tab.key"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <!-- ---------- 账号登录 ---------- -->
        <div v-if="activeTab === 'account'" class="tab-body">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            hide-required-asterisk
            @submit.prevent
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model="form.username"
                size="large"
                placeholder="学号 / 手机号 / admin"
                autocomplete="username"
                clearable
                @keyup.enter="onSubmit"
              >
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                size="large"
                type="password"
                show-password
                placeholder="请输入登录密码"
                autocomplete="new-password"
                @keyup.enter="onSubmit"
              >
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>

            <el-form-item label="验证码" prop="captcha">
              <div class="captcha-row">
                <el-input
                  v-model="form.captcha"
                  size="large"
                  maxlength="4"
                  placeholder="请输入右侧 4 位验证码"
                  autocomplete="off"
                  @keyup.enter="onSubmit"
                />
                <CaptchaImage ref="captchaRef" />
              </div>
            </el-form-item>

            <el-button
              class="submit"
              type="primary"
              size="large"
              :loading="loading"
              :disabled="lockSeconds > 0"
              @click="onSubmit"
            >
              {{ lockSeconds > 0 ? `请 ${lockSeconds} 秒后重试` : '登 录' }}
            </el-button>
          </el-form>

          <p class="form-hint">
            <el-icon><Lock /></el-icon>
            <span>验证码在浏览器本地生成校验，不请求服务器；密码只随登录请求发送，不写入浏览器存储。</span>
          </p>
        </div>

        <!-- ---------- 短信登录 ---------- -->
        <div v-else-if="activeTab === 'sms'" class="tab-body">
          <SmsLoginPanel
            ref="smsPanelRef"
            :demo-enabled="smsDemoEnabled"
            :account="smsDemoAccount"
            :submitting="loading"
            @login="onSmsLogin"
          />
        </div>

        <!-- ---------- 扫码登录 ---------- -->
        <div v-else class="tab-body">
          <QrLoginPanel
            ref="qrPanelRef"
            :demo-enabled="qrDemoEnabled"
            :account="demoAccount"
            @confirm="onQrConfirm"
          />
        </div>

        <footer class="form-foot">
          <span>还没有账号？</span>
          <el-button link type="primary" @click="router.push('/register')">立即注册</el-button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import CaptchaImage from '@/components/login/CaptchaImage.vue'
import QrLoginPanel from '@/components/login/QrLoginPanel.vue'
import SmsLoginPanel from '@/components/login/SmsLoginPanel.vue'
import { ROLE_HOME, useUserStore } from '@/store/user'
import { verifyCaptcha } from '@/utils/captcha'
import {
  LOCK_SECONDS,
  MAX_FAILURES,
  getLockRemainingSeconds,
  registerLoginFailure,
  resetLoginGuard
} from '@/utils/loginGuard'

type TabKey = 'account' | 'sms' | 'qr'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'account', label: '账号登录' },
  { key: 'sms', label: '短信登录' },
  { key: 'qr', label: '扫码登录' }
]

/** 左侧插画里的柱状图高度（纯 CSS 装饰，不参与业务） */
const artBars = [38, 62, 45, 78, 52, 88, 60, 72, 44, 66, 50, 84]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaImage>>()
const qrPanelRef = ref<InstanceType<typeof QrLoginPanel>>()
const smsPanelRef = ref<InstanceType<typeof SmsLoginPanel>>()

const loading = ref(false)
// 支持 #/login?tab=sms / ?tab=qr 直接打开对应标签（方便演示与截图）
const activeTab = ref<TabKey>(
  route.query.tab === 'qr' ? 'qr' : route.query.tab === 'sms' ? 'sms' : 'account'
)
const lockSeconds = ref(getLockRemainingSeconds())

let lockTicker: ReturnType<typeof setInterval> | undefined

const form = reactive({ username: '', password: '', captcha: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码为 4 位字符', trigger: 'blur' }
  ]
}

/* -------------------- 扫码演示配置 -------------------- */
/** 本地演示账号：只在 .env.local 里配置，不写进源码，避免把演示口令打进前端包 */
const demoAccount = (import.meta.env.VITE_LOGIN_QR_DEMO_ACCOUNT || '').trim()
const demoPassword = (import.meta.env.VITE_LOGIN_QR_DEMO_PASSWORD || '').trim()

/** 是否允许「模拟手机扫码/确认」：显式关闭 > 显式开启 > 默认仅开发环境 */
const qrDemoEnabled = computed(() => {
  const flag = import.meta.env.VITE_LOGIN_QR_DEMO
  if (flag === 'false') {
    return false
  }
  if (flag === 'true') {
    return true
  }
  return import.meta.env.DEV
})

/* -------------------- 短信登录演示配置 -------------------- */
/** 短信登录的本地演示账号（同样只放 .env.local） */
const smsDemoAccount = (import.meta.env.VITE_LOGIN_SMS_DEMO_ACCOUNT || '').trim()
const smsDemoPassword = (import.meta.env.VITE_LOGIN_SMS_DEMO_PASSWORD || '').trim()

const smsDemoEnabled = computed(() => {
  const flag = import.meta.env.VITE_LOGIN_SMS_DEMO
  if (flag === 'false') {
    return false
  }
  if (flag === 'true') {
    return true
  }
  return import.meta.env.DEV
})

/* -------------------- 失败锁定倒计时 -------------------- */
function startLockCountdown() {
  stopLockCountdown()
  lockTicker = setInterval(() => {
    const remain = getLockRemainingSeconds()
    lockSeconds.value = remain
    if (remain <= 0) {
      stopLockCountdown()
    }
  }, 1000)
}

function stopLockCountdown() {
  if (lockTicker) {
    clearInterval(lockTicker)
    lockTicker = undefined
  }
}

/** 一次失败后的统一处理：计数、必要时锁定、换验证码 */
function handleFailure(tip?: string) {
  const locked = registerLoginFailure()
  if (locked > 0) {
    lockSeconds.value = locked
    startLockCountdown()
    ElMessage.warning(`连续输错 ${MAX_FAILURES} 次，已锁定 ${LOCK_SECONDS} 秒`)
  } else if (tip) {
    ElMessage.warning(tip)
  }
}

/* -------------------- 登录跳转 -------------------- */
function goHome(role: string) {
  const redirect = route.query.redirect as string | undefined
  router.push(redirect || ROLE_HOME[role] || '/login')
}

/* -------------------- 账号登录 -------------------- */
async function onSubmit() {
  if (lockSeconds.value > 0) {
    ElMessage.warning(`尝试过于频繁，请 ${lockSeconds.value} 秒后再试`)
    return
  }

  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  // 第一道门槛：本地图形验证码（挡住不带渲染能力的重放脚本）
  if (!verifyCaptcha(form.captcha, captchaRef.value?.getCode() ?? '')) {
    form.captcha = ''
    captchaRef.value?.refresh()
    handleFailure('验证码不正确，已为你换一张')
    return
  }

  loading.value = true
  try {
    const result = await userStore.login({
      username: form.username.trim(),
      password: form.password
    })
    resetLoginGuard()
    lockSeconds.value = 0
    stopLockCountdown()
    ElMessage.success('登录成功')
    goHome(result.role)
  } catch {
    // 失败后清掉密码与验证码并换一张，减少残留数据被读取或重放的机会
    form.password = ''
    form.captcha = ''
    captchaRef.value?.refresh()
    handleFailure()
  } finally {
    loading.value = false
  }
}

/* -------------------- 扫码登录（本地演示） -------------------- */
async function onQrConfirm() {
  if (!demoAccount || !demoPassword) {
    ElMessage.warning(
      '扫码登录本地演示需在 frontend/.env.local 配置 VITE_LOGIN_QR_DEMO_ACCOUNT 与 VITE_LOGIN_QR_DEMO_PASSWORD'
    )
    qrPanelRef.value?.reset()
    return
  }
  loading.value = true
  try {
    const result = await userStore.login({ username: demoAccount, password: demoPassword })
    resetLoginGuard()
    lockSeconds.value = 0
    stopLockCountdown()
    ElMessage.success('扫码登录成功（本地演示）')
    goHome(result.role)
  } catch {
    qrPanelRef.value?.reset()
  } finally {
    loading.value = false
  }
}

/* -------------------- 短信登录（本地演示） -------------------- */
/**
 * 短信验证码本身完全在前端校验（见 utils/smsCode.ts），
 * 校验通过后仍要落到真实的 /auth/login 才能拿到 JWT，
 * 而该接口只认账号+密码，所以这里用本地演示账号换取登录态。
 * 后端接入短信登录后，这里应改为 POST /auth/sms/login 直接拿 token。
 */
async function onSmsLogin(phone: string) {
  if (!smsDemoAccount || !smsDemoPassword) {
    ElMessage.warning(
      '短信登录本地演示需在 frontend/.env.local 配置 VITE_LOGIN_SMS_DEMO_ACCOUNT 与 VITE_LOGIN_SMS_DEMO_PASSWORD'
    )
    return
  }
  loading.value = true
  try {
    const result = await userStore.login({ username: smsDemoAccount, password: smsDemoPassword })
    resetLoginGuard()
    lockSeconds.value = 0
    stopLockCountdown()
    ElMessage.success(`短信登录成功（本地演示，手机号 ${phone}）`)
    goHome(result.role)
  } catch {
    smsPanelRef.value?.reset()
  } finally {
    loading.value = false
  }
}

/* -------------------- 其它 -------------------- */
function switchTab(key: TabKey) {
  if (activeTab.value === key) {
    return
  }
  activeTab.value = key
  formRef.value?.clearValidate()
}

onMounted(() => {
  if (lockSeconds.value > 0) {
    startLockCountdown()
  }
})

onBeforeUnmount(stopLockCountdown)
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 18%, #eef3ff 0%, rgba(238, 243, 255, 0) 45%),
    radial-gradient(circle at 82% 78%, #f2eefe 0%, rgba(242, 238, 254, 0) 42%),
    #f7f9fc;
}

.login-shell {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 580px;
  overflow: hidden;
  background: #fff;
  border-radius: 18px;
  box-shadow:
    0 24px 60px rgba(31, 60, 120, 0.1),
    0 2px 6px rgba(31, 60, 120, 0.04);
}

/* ---------------- 左侧品牌区 ---------------- */
.brand-side {
  position: relative;
  display: flex;
  flex: 0 0 430px;
  flex-direction: column;
  padding: 44px 40px 30px;
  overflow: hidden;
  background: linear-gradient(165deg, #f7f9ff 0%, #eef3ff 55%, #f5f3ff 100%);
  border-right: 1px solid #eef1f7;
}
.brand-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #2f6bff, #7b5bff);
  border-radius: 9px;
}
.brand-name {
  font-size: 14px;
  font-weight: 600;
  color: #2a3550;
  letter-spacing: 0.5px;
}
.brand-slogan {
  margin: 34px 0 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.55;
  color: #1b2a4a;
  letter-spacing: 1px;
}
.brand-art {
  position: relative;
  flex: 1;
  min-height: 260px;
  margin: 6px 0;
}
.ring {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(47, 107, 255, 0.16);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}
.ring-1 {
  width: 288px;
  height: 288px;
}
.ring-2 {
  width: 216px;
  height: 216px;
}
.ring-3 {
  width: 148px;
  height: 148px;
}
.art-card {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 210px;
  padding: 14px 14px 10px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 16px 32px rgba(47, 107, 255, 0.14);
  transform: translate(-52%, -54%) rotate(-8deg);
}
.art-bars {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  height: 62px;
}
.art-bars span {
  flex: 1;
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, #9db4ff, #6f8dff);
}
.art-lines {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 10px;
}
.art-lines span {
  height: 5px;
  background: #eef1f8;
  border-radius: 3px;
}
.art-lines span:last-child {
  width: 62%;
}
.tile {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  font-size: 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 20px rgba(31, 60, 120, 0.12);
  animation: float 3.6s ease-in-out infinite;
}
.tile-1 {
  top: 4%;
  left: 50%;
  animation-delay: 0s;
}
.tile-2 {
  top: 14%;
  left: 10%;
  animation-delay: 0.4s;
}
.tile-3 {
  top: 6%;
  left: 78%;
  animation-delay: 0.8s;
}
.tile-4 {
  bottom: 20%;
  left: 82%;
  animation-delay: 1.2s;
}
.tile-5 {
  bottom: 6%;
  left: 18%;
  animation-delay: 1.6s;
}
.tile-6 {
  bottom: 12%;
  left: 58%;
  animation-delay: 2s;
}
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}
.brand-foot {
  margin: 0;
  font-size: 12px;
  color: #8a93a8;
  letter-spacing: 2px;
}

/* ---------------- 右侧表单区 ---------------- */
.form-side {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  padding: 46px 56px 28px;
  background: #fff;
}
.form-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #1b2a4a;
}
.form-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: #8a93a8;
}
.tabs {
  display: flex;
  gap: 36px;
  margin: 26px 0 22px;
  border-bottom: 1px solid #eef1f7;
}
.tab {
  position: relative;
  padding: 0 0 12px;
  font-size: 15px;
  color: #606a7b;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
}
.tab:hover {
  color: var(--campus-primary);
}
.tab.is-active {
  font-weight: 600;
  color: var(--campus-primary);
}
.tab.is-active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  content: '';
  background: var(--campus-primary);
  border-radius: 2px;
}
.tab-body {
  flex: 1;
}
:deep(.el-form-item__label) {
  padding-bottom: 4px;
  font-size: 13px;
  color: #4a5568;
}
.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1 1 auto;
  min-width: 0;
}
.submit {
  width: 100%;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
}
.form-hint {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #a0a5ad;
}
.form-hint .el-icon {
  margin-top: 3px;
  color: #b6bcc6;
}
.form-foot {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;
  padding-top: 22px;
  margin-top: 22px;
  font-size: 13px;
  color: #8a93a8;
  border-top: 1px solid #f2f4f8;
}

@media (max-width: 900px) {
  .login-shell {
    max-width: 460px;
    min-height: auto;
  }
  .brand-side {
    display: none;
  }
  .form-side {
    padding: 36px 28px 24px;
  }
}
</style>
