/**
 * 登录失败节流（纯前端）
 *
 * 作用：连续输错若干次后，按钮先锁 30 秒再让重试，避免「手速测试」式的连环尝试，
 *       也给正常用户一个明确反馈，而不是无脑对着接口狂刷。
 *
 * 边界说明：状态存在 sessionStorage，用户可以清掉或直接调接口绕过，
 *          所以它不是安全边界。真正的防爆破在服务端：
 *          AuthController#login 上的 @RateLimit(limit = 10, seconds = 60, by = IP)，
 *          以及「账号或密码错误」统一提示（不暴露账号是否存在）。
 */

const STORAGE_KEY = 'campus_login_guard'

/** 允许的连续失败次数 */
export const MAX_FAILURES = 5

/** 触发锁定时长（秒） */
export const LOCK_SECONDS = 30

interface GuardState {
  /** 连续失败次数 */
  failures: number
  /** 锁定截止时间戳（毫秒），0 表示未锁定 */
  lockedUntil: number
}

function readState(): GuardState {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { failures: 0, lockedUntil: 0 }
    }
    const parsed = JSON.parse(raw) as Partial<GuardState>
    return {
      failures: typeof parsed.failures === 'number' ? parsed.failures : 0,
      lockedUntil: typeof parsed.lockedUntil === 'number' ? parsed.lockedUntil : 0
    }
  } catch {
    return { failures: 0, lockedUntil: 0 }
  }
}

function writeState(state: GuardState) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // 隐私模式 / 存储被禁用时静默降级，不影响登录主流程
  }
}

/** 当前锁定剩余秒数，0 表示可继续提交 */
export function getLockRemainingSeconds(): number {
  const state = readState()
  const remaining = Math.ceil((state.lockedUntil - Date.now()) / 1000)
  return remaining > 0 ? remaining : 0
}

/** 当前连续失败次数（用于界面提示） */
export function getFailureCount(): number {
  return readState().failures
}

/**
 * 记录一次失败：达到阈值后进入锁定，并把计数清零重新开始。
 * @returns 锁定剩余秒数，0 表示还可以继续尝试
 */
export function registerLoginFailure(): number {
  const state = readState()
  const failures = state.failures + 1
  if (failures >= MAX_FAILURES) {
    const lockedUntil = Date.now() + LOCK_SECONDS * 1000
    writeState({ failures: 0, lockedUntil })
    return LOCK_SECONDS
  }
  writeState({ failures, lockedUntil: 0 })
  return 0
}

/** 登录成功后清空计数与锁定 */
export function resetLoginGuard() {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 忽略
  }
}
