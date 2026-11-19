/**
 * 本地短信验证码（前端生成 + 前端校验，不请求服务器）
 *
 * 为什么在前端：项目还没接入短信网关（阿里云/腾讯云短信都要资质与费用），
 * 因此验证码在本机随机生成、本机保管、本机比对，页面会明确标注「本地演示」。
 *
 * 边界说明：这是演示用的门禁，不是安全边界。
 *   真正上线时应该：后端下发验证码 → Redis 存 TTL → 校验一次性消费 → 按手机号+IP 限流，
 *   并做「60 秒内不可重发、5 分钟内有效、最多试 5 次」的服务端约束（这里的常量就是按它写的）。
 */

/** 验证码位数 */
export const SMS_CODE_LENGTH = 6

/** 验证码有效期（秒） */
export const SMS_TTL_SECONDS = 300

/** 重发间隔（秒） */
export const SMS_RESEND_SECONDS = 60

/** 同一个验证码最多尝试次数 */
export const SMS_MAX_ATTEMPTS = 5

export interface SmsChallenge {
  /** 发送到的手机号（已规范化） */
  phone: string
  /** 验证码原文，仅存在于内存 */
  code: string
  /** 过期时间戳（毫秒） */
  expiresAt: number
  /** 已尝试次数 */
  attempts: number
}

export type SmsVerifyResult = 'ok' | 'empty' | 'expired' | 'mismatch' | 'locked'

function randomInt(max: number): number {
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const buffer = new Uint32Array(1)
    crypto.getRandomValues(buffer)
    return buffer[0] % max
  }
  return Math.floor(Math.random() * max)
}

/** 去掉空格、短横线等分隔符 */
export function normalizePhone(input: string): string {
  return (input || '').replace(/[\s-]/g, '')
}

/** 中国大陆手机号：1 开头、第二位 3~9、共 11 位 */
export function isValidPhone(input: string): boolean {
  return /^1[3-9]\d{9}$/.test(normalizePhone(input))
}

/** 手机号脱敏：138****8000 */
export function maskPhone(phone: string): string {
  const normalized = normalizePhone(phone)
  if (normalized.length !== 11) {
    return normalized
  }
  return `${normalized.slice(0, 3)}****${normalized.slice(7)}`
}

/** 生成 6 位数字验证码（允许前导 0） */
export function createSmsCode(): string {
  let code = ''
  for (let i = 0; i < SMS_CODE_LENGTH; i++) {
    code += String(randomInt(10))
  }
  return code
}

/** 生成一次验证码挑战 */
export function createSmsChallenge(phone: string, now = Date.now()): SmsChallenge {
  return {
    phone: normalizePhone(phone),
    code: createSmsCode(),
    expiresAt: now + SMS_TTL_SECONDS * 1000,
    attempts: 0
  }
}

/** 校验并返回结果；不修改入参，由调用方决定是否累加 attempts */
export function verifySmsChallenge(
  challenge: SmsChallenge | null,
  input: string,
  now = Date.now()
): SmsVerifyResult {
  if (!challenge) {
    return 'empty'
  }
  if (challenge.attempts >= SMS_MAX_ATTEMPTS) {
    return 'locked'
  }
  if (now > challenge.expiresAt) {
    return 'expired'
  }
  const value = (input || '').trim()
  if (!value) {
    return 'mismatch'
  }
  return value === challenge.code ? 'ok' : 'mismatch'
}

/** 距离可重发还剩几秒 */
export function resendRemainingSeconds(sentAt: number, now = Date.now()): number {
  const remain = SMS_RESEND_SECONDS - Math.floor((now - sentAt) / 1000)
  return remain > 0 ? remain : 0
}
