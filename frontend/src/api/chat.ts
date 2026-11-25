import { get, post } from '@/utils/request'

/** 一条订单聊天消息 */
export interface ChatMessage {
  id: string
  orderId: string
  fromUserId: string
  fromRole: string
  content: string
  createTime: string
}

/** 拉取订单聊天记录（仅订单参与者可见） */
export function listMessages(orderId: string) {
  return get<ChatMessage[]>(`/chat/${orderId}/messages`)
}

/** REST 兜底发送（正常走 WebSocket） */
export function sendMessage(orderId: string, content: string) {
  return post<ChatMessage>(`/chat/${orderId}/messages`, { content })
}