<template>
  <div class="sms-panel">
    <el-form label-position="top" hide-required-asterisk @submit.prevent>
      <el-form-item label="手机号" :error="phoneError">
        <el-input
          v-model="phone"
          size="large"
          maxlength="11"
          placeholder="请输入手机号"
          autocomplete="tel"
          clearable
          @keyup.enter="onSubmit"
        >
          <template #prefix>
            <span class="region">+86</span>
          </template>
          <template #append>
            <el-button :disabled="resendRemain > 0 || submitting" @click="sendCode">
              {{ resendRemain > 0 ? `${resendRemain}s 后重发` : '获取验证码' }}
            </el-button>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="验证码" :error="codeError">
        <el-input
          v-model="code"
          size="large"
          maxlength="6"
          placeholder="请输入 6 位验证码"
          autocomplete="one-time-code"
          @keyup.enter="onSubmit"
        />
      </el-form-item>

      <el-button
        class="submit"
        type="primary"
        size="large"
        :loading="submitting"
        @click="onSubmit"
      >
        登 录
      </el-button>
    </el-form>

    <p class="sms-note">未注册过的手机号将自动注册（需后端开放短信登录，当前为本地演示）</p>

    <div v-if="demoEnabled" class="sms-demo">
      <p class="sms-demo-title">
        <el-icon><WarnTriangleFilled /></el-icon>
        本地演示模式：未接入短信网关
      </p>
      <p v-if="challenge" class="sms-demo-code">
        本次验证码
        <b>{{ challenge.code }}</b>
        <el-button link type="primary" @click="fillCode">一键填入</el-button>
        <span class="sms-demo-ttl">（{{ ttlRemain }} 秒内有效，剩余 {{ leftAttempts }} 次尝试）</span>
      </p>
      <p v-else class="sms-demo-tip">点「获取验证码」后，验证码会显示在这里</p>
      <p v-if="!account" class="sms-demo-warn">
        未配置演示账号：请在本机 .env.local 填写 VITE_LOGIN_SMS_DEMO_ACCOUNT 与 _PASSWORD
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import {
  SMS_MAX_ATTEMPTS,
  SMS_RESEND_SECONDS,
  SMS_TTL_SECONDS,
  createSmsChallenge,
  isValidPhone,
  normalizePhone,
  resendRemainingSeconds,
  verifySmsChallenge,
  type SmsChallenge
} from '@/utils/smsCode'

const props = withDefaults(
  defineProps<{
    /** 是否展示「本地演示」信息（含验证码明文） */
    demoEnabled?: boolean
    /** 演示账号，仅用于提示是否已配置 */
    account?: string
    /** 父组件登录请求进行中 */
    submitting?: boolean
  }>(),
  { demoEnabled: false, account: '', submitting: false }
)

const emit = defineEmits<{ (event: 'login', phone: string): void }>()

const phone = ref('')
const code = ref('')
const phoneError = ref('')
const codeError = ref('')

/** 当前有效的验证码挑战（只存在内存里，不写存储） */
const challenge = ref<SmsChallenge | null>(null)
const sentAt = ref(0)
const resendRemain = ref(0)
const ttlRemain = ref(0)

let ticker: ReturnType<typeof setInterval> | undefined

const leftAttempts = computed(() => Math.max(0, SMS_MAX_ATTEMPTS - (challenge.value?.attempts ?? 0)))

function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = undefined
  }
}

function startTicker() {
  stopTicker()
  ticker = setInterval(() => {
    const now = Date.now()
    resendRemain.value = resendRemainingSeconds(sentAt.value, now)
    ttlRemain.value = challenge.value
      ? Math.max(0, Math.ceil((challenge.value.expiresAt - now) / 1000))
      : 0
    if (resendRemain.value <= 0 && ttlRemain.value <= 0) {
      stopTicker()
    }
  }, 1000)
}

/** 清掉当前验证码（换手机号 / 错误次数用尽 / 过期重发时调用） */
function resetChallenge() {
  challenge.value = null
  code.value = ''
  resendRemain.value = 0
  ttlRemain.value = 0
  sentAt.value = 0
  stopTicker()
}

/**
 * 获取验证码：本地生成，不发起任何请求。
 * 真实项目这里应调用后端（如 POST /auth/sms/send），由后端限流并落 Redis。
 */
function sendCode() {
  if (resendRemain.value > 0 || props.submitting) {
    return
  }
  const normalized = normalizePhone(phone.value)
  if (!isValidPhone(normalized)) {
    phoneError.value = '请输入正确的 11 位手机号'
    return
  }
  phoneError.value = ''
  codeError.value = ''

  challenge.value = createSmsChallenge(normalized)
  sentAt.value = Date.now()
  code.value = ''
  resendRemain.value = SMS_RESEND_SECONDS
  ttlRemain.value = SMS_TTL_SECONDS
  startTicker()
  ElMessage.success('验证码已生成（本地演示，未发送真实短信）')
}

function fillCode() {
  if (challenge.value) {
    code.value = challenge.value.code
    codeError.value = ''
  }
}

function onSubmit() {
  if (props.submitting) {
    return
  }
  const normalized = normalizePhone(phone.value)
  if (!isValidPhone(normalized)) {
    phoneError.value = '请输入正确的 11 位手机号'
    return
  }
  phoneError.value = ''

  const current = challenge.value
  if (!current) {
    codeError.value = '请先获取验证码'
    return
  }
  if (!code.value.trim()) {
    codeError.value = '请输入验证码'
    return
  }
  const result = verifySmsChallenge(current, code.value)

  if (result === 'expired') {
    codeError.value = '验证码已过期，请重新获取'
    return
  }
  if (result === 'locked') {
    codeError.value = '错误次数过多，请重新获取验证码'
    return
  }
  if (result === 'mismatch') {
    current.attempts += 1
    if (current.attempts >= SMS_MAX_ATTEMPTS) {
      codeError.value = '验证码错误次数过多，请重新获取'
      resetChallenge()
      return
    }
    codeError.value = `验证码不正确，还可尝试 ${SMS_MAX_ATTEMPTS - current.attempts} 次`
    return
  }

  codeError.value = ''
  emit('login', normalized)
}

// 换了手机号，之前的验证码就对不上了，直接作废，避免误判
watch(phone, () => {
  if (challenge.value && normalizePhone(phone.value) !== challenge.value.phone) {
    resetChallenge()
    codeError.value = ''
  }
})

onBeforeUnmount(stopTicker)

defineExpose({
  /** 供父组件清理（例如登录失败后） */
  reset: () => {
    resetChallenge()
    code.value = ''
    codeError.value = ''
  }
})
</script>

<style scoped>
.sms-panel {
  padding-top: 4px;
}
.region {
  padding-right: 6px;
  font-size: 14px;
  color: #303133;
  border-right: 1px solid #e4e7ed;
  margin-right: 6px;
}
.submit {
  width: 100%;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
}
.sms-note {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #a0a5ad;
}
.sms-demo {
  padding: 10px 12px;
  margin-top: 16px;
  background: #f7f9ff;
  border: 1px dashed #d5ddf5;
  border-radius: 8px;
}
.sms-demo-title {
  display: flex;
  gap: 4px;
  align-items: center;
  margin: 0 0 8px;
  font-size: 12px;
  color: #7c8aa5;
}
.sms-demo-code {
  margin: 0;
  font-size: 13px;
  color: #303133;
}
.sms-demo-code b {
  padding: 1px 6px;
  margin: 0 4px 0 2px;
  font-size: 15px;
  letter-spacing: 3px;
  color: var(--campus-primary);
  background: #eef3ff;
  border-radius: 4px;
}
.sms-demo-ttl {
  font-size: 12px;
  color: #a0a5ad;
}
.sms-demo-tip {
  margin: 0;
  font-size: 12px;
  color: #a0a5ad;
}
.sms-demo-warn {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #d97706;
}
:deep(.el-input-group__append) {
  padding: 0;
}
:deep(.el-input-group__append .el-button) {
  margin: 0;
  color: var(--campus-primary);
  border: none;
  box-shadow: none;
}
:deep(.el-input-group__append .el-button.is-disabled) {
  color: #b3b8c2;
  background: transparent;
}
</style>
