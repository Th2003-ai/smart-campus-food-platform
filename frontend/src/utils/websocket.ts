import { ElMessage } from 'element-plus'

import { TOKEN_KEY } from '@/utils/request'

/**
 * 订单实时通知 WebSocket 客户端。
 *
 * 后端端点：/ws/order?token=<jwt>，握手期解析 JWT 并把连接按 userId / role 归组。
 * 消息格式：{ type: 'ORDER_STATUS' | 'NEW_ORDER' | 'GRAB_ORDER', data: {...} }
 *
 * 使用方式（各端在布局组件 onMounted 里 connect，onUnmounted 里 close）：
 *   const ws = new OrderSocket()
 *   ws.on('ORDER_STATUS', (data) => { ... })
 *   ws.connect()
 */
export type MessageHandler = (data: any) => void

const RECONNECT_DELAY = 3000
const MAX_RECONNECT = 5
/** 心跳间隔：后端骑手在线标记 TTL 为 60 秒，这里 25 秒发一次 ping 续期，确保不会被判离线 */
const HEARTBEAT_INTERVAL = 25000

/**
 * 计算 WebSocket 端点。
 *
 * <p>默认「跟着页面地址走」：用当前页面协议与 host 拼接，
 * 因此本机用 localhost、别人用 http://192.168.x.x:5173 访问时都能连上，
 * 不需要为每台机器改配置。开发态由 vite.config.ts 的 /api 代理转发 ws 升级。
 *
 * <p>仅当需要跨域连接（前端与后端不同源）时，才用 VITE_WS_URL 显式指定。
 */
function resolveWsUrl(): string {
  const configured = import.meta.env.VITE_WS_URL
  if (configured) {
    return configured
  }
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/api/ws/order`
}

export class OrderSocket {
  private ws: WebSocket | null = null
  private handlers = new Map<string, MessageHandler[]>()
  private reconnectCount = 0
  private manualClosed = false
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null

  connect() {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
      return
    }
    this.ws = new WebSocket(`${resolveWsUrl()}?token=${token}`)

    this.ws.onopen = () => {
      this.reconnectCount = 0
      this.startHeartbeat()
    }

    this.ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        this.emit(payload.type, payload.data)
      } catch {
        // 忽略无法解析的消息，避免影响页面
      }
    }

    this.ws.onclose = () => {
      this.stopHeartbeat()
      if (!this.manualClosed && this.reconnectCount < MAX_RECONNECT) {
        this.reconnectCount += 1
        setTimeout(() => this.connect(), RECONNECT_DELAY)
      }
    }

    this.ws.onerror = () => {
      // 连接失败由 onclose 统一处理，这里只提示一次
      if (this.reconnectCount === 0) {
        ElMessage.warning('实时通知连接中断，正在重连')
      }
    }
  }

  on(type: string, handler: MessageHandler) {
    const list = this.handlers.get(type) || []
    list.push(handler)
    this.handlers.set(type, list)
  }

  private emit(type: string, data: any) {
    ;(this.handlers.get(type) || []).forEach((handler) => handler(data))
  }

  close() {
    this.manualClosed = true
    this.stopHeartbeat()
    this.ws?.close()
    this.ws = null
  }

  /** 发送一帧消息（订单聊天用：{ type: 'CHAT', orderId, content }） */
  send(type: string, data: Record<string, unknown>): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({ type, ...data }))
      return true
    }
    return false
  }

  /** 是否已连接（组件据此决定要不要退回 REST 发送） */
  get connected(): boolean {
    return !!this.ws && this.ws.readyState === WebSocket.OPEN
  }

  /** 连接成功后定时发送心跳，续期后端 Redis 在线标记（TTL 60s） */
  private startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send('ping')
      }
    }, HEARTBEAT_INTERVAL)
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }
}
