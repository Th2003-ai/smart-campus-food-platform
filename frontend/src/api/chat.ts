import { get, post } from '@/utils/request'

/** 一条聊天消息 */
export interface ChatMessage {
  id: string
  /** 会话 ID：订单ID / dm:订单ID:A-B / svc:订单ID */
  orderId: string
  fromUserId: string
  fromRole: string
  content: string
  createTime: string
}

/** 客服介入返回 */
export interface ServiceTicket {
  ticketId: string
  ticketNo: string
  threadId: string
}

/** 群聊 threadId：一个订单一个群，学生/商户/骑手三方都在里面 */
export function orderThread(orderId: string) {
  return orderId
}

/**
 * 单聊 threadId：角色码按字母序拼，双方算出来的 key 才一致。
 * 例：学生点「联系商家」→ dm:{orderId}:MERCHANT-STUDENT
 */
export function dmThread(orderId: string, roleA: string, roleB: string) {
  return `dm:${orderId}:${[roleA.toUpperCase(), roleB.toUpperCase()].sort().join('-')}`
}

/** 客服会话 threadId */
export function serviceThread(orderId: string) {
  return `svc:${orderId}`
}

/** 拉取会话记录（只读得到自己有资格的会话） */
export function listMessages(threadId: string) {
  return get<ChatMessage[]>(`/chat/${encodeURIComponent(threadId)}/messages`)
}

/** REST 兜底发送（正常走 WebSocket） */
export function sendMessage(threadId: string, content: string) {
  return post<ChatMessage>(`/chat/${encodeURIComponent(threadId)}/messages`, { content })
}

/** 申请平台客服介入：建工单 + 通知在线管理员，返回客服会话 ID */
export function requestService(orderId: string) {
  return post<ServiceTicket>(`/chat/${encodeURIComponent(orderId)}/service`)
}