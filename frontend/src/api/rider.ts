import { get, post, put } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { Order } from '@/api/order'

/**
 * 骑手档案（对应后端 Rider 实体真实字段）
 *
 * 注意：后端没有 name / phone / totalOrders / score，
 * 姓名字段是 realName，累计收入是 totalIncome，平均配送时长是 avgDeliveryTime。
 */
export interface Rider {
  id: string
  userId?: string
  /** 真实姓名 */
  realName?: string
  studentCardNo?: string
  vehicleType?: string
  /** 0 休息中 / 1 接单中 */
  workStatus?: number
  /** 当前配送中订单数 */
  deliveringCount?: number
  /** 同时最大接单数 */
  maxDelivering?: number
  /** 累计收入 */
  totalIncome?: number
  /** 平均配送时长（分钟） */
  avgDeliveryTime?: number
  /** 0 待审核 1 已通过 2 已驳回 */
  auditStatus?: number
  auditRemark?: string
}

/**
 * 收入统计。
 *
 * 坑：后端用 JdbcTemplate/MyBatis 返回 Map，key 是 SQL 里的列别名（下划线），
 * MyBatis 的 map-underscore-to-camel-case 只对实体映射生效，对 Map 结果不生效。
 * 所以这里是 today_income 而不是 todayIncome。
 * 另外 totalIncome / online / lastHeartbeat 是 Service 层后加的驼峰 key。
 */
export interface RiderIncome {
  today_income?: number
  week_income?: number
  month_income?: number
  today_count?: number
  month_count?: number
  totalIncome?: number
  /** 是否在线（Redis 在线标记有效期内） */
  online?: boolean
  /** 最后心跳时间戳（毫秒） */
  lastHeartbeat?: number
}

/** 抢单大厅：待取餐且未被抢的订单 */
export function listGrabHall(params?: { pageNum?: number; pageSize?: number }) {
  return get<PageResult<Order>>('/rider/hall', params)
}

/** 抢单（后端有 Redis 分布式锁，失败会返回「手慢了…」） */
export function grabOrder(orderId: string) {
  return post<Order>(`/rider/grab/${orderId}`)
}

export function pickupOrder(orderId: string) {
  return post<Order>(`/rider/pickup/${orderId}`)
}

export function deliverOrder(orderId: string) {
  return post<Order>(`/rider/deliver/${orderId}`)
}

export function listRiderOrders(params?: { status?: number; pageNum?: number; pageSize?: number }) {
  return get<PageResult<Order>>('/rider/orders', params)
}

export function getIncome() {
  return get<RiderIncome>('/rider/income')
}

/** 工作状态：1 接单中 / 0 休息中。后端是 @RequestParam，必须走 query 参数 */
export function updateWorkStatus(workStatus: number) {
  return put<void>(`/rider/work-status?workStatus=${workStatus}`)
}

/** 上线心跳：续期 Redis 在线标记（60 秒无心跳即视为离线） */
export function sendHeartbeat() {
  return post<{ riderId: string; online: boolean }>('/rider/heartbeat')
}

export function getRiderProfile() {
  return get<Rider>('/rider/profile')
}
