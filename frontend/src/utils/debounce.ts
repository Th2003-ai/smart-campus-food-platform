/**
 * 防抖 / 节流工具：用来收敛短时间内被高频触发的操作，
 * 避免“用户不停点、请求就不停发”把后端打满。
 */

/**
 * 防抖（debounce）：在连续触发结束、停顿 delay 毫秒后才真正执行一次；
 * 期间再次触发会重新计时。典型场景：搜索框实时联想、按钮防连点、窗口 resize。
 *
 * @param fn    实际要执行的函数
 * @param delay 停顿多久后执行（毫秒），默认 300ms
 * @returns 防抖后的函数（带 cancel 方法，可取消尚未执行的调用）
 */
export function debounce<T extends (...args: any[]) => void>(
  fn: T,
  delay = 300
): ((...args: Parameters<T>) => void) & { cancel: () => void } {
  let timer: ReturnType<typeof setTimeout> | null = null

  const debounced = function (this: unknown, ...args: Parameters<T>) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      fn.apply(this, args)
    }, delay)
  }

  debounced.cancel = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  return debounced
}

/**
 * 节流（throttle）：按固定频率执行，delay 内最多执行一次。
 * 典型场景：滚动加载、鼠标移动、按钮在指定时间内只允许一次。
 *
 * @param fn    实际要执行的函数
 * @param delay 执行间隔（毫秒），默认 300ms
 */
export function throttle<T extends (...args: any[]) => void>(
  fn: T,
  delay = 300
): (...args: Parameters<T>) => void {
  let last = 0
  return function (this: unknown, ...args: Parameters<T>) {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn.apply(this, args)
    }
  }
}
