import { get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 订单状态：0 待接单 1 备餐中 2 待取餐 3 配送中 4 已完成 5 已取消 6 申诉中 */
/** 订单（对应后端 Order 实体，字段以后端为准） */
export interface Order {
  id: string
  orderNo: string
  userId?: string
  shopId?: string
  riderId?: string
  goodsAmount?: number
  deliveryFee?: number
  totalAmount: number
  /** 1 余额 2 模拟第三方 */
  payType?: number
  /** 0 未支付 1 已支付 2 已退款 */
  payStatus?: number
  payTime?: string
  /** 0 待接单 1 备餐中 2 待取餐 3 配送中 4 已完成 5 已取消 6 申诉中 */
  orderStatus: number
  /** 1 骑手配送 2 宿舍楼下自取 */
  deliveryType?: number
  receiver?: string
  receiverPhone?: string
  address?: string
  remark?: string
  /** 取餐码，商户出餐时生成 */
  pickupCode?: string
  /** 取消/拒单原因 */
  cancelReason?: string
  expectTime?: string
  finishTime?: string
  createTime?: string
  updateTime?: string
}

/** 订单明细（成交时快照，菜品改名不影响历史订单） */
export interface OrderItem {
  id?: string
  orderId?: string
  dishId: string
  dishName: string
  dishImage?: string
  specName?: string
  price: number
  quantity: number
  amount: number
}

export interface OrderVO {
  order: Order
  items: OrderItem[]
  statusDesc?: string
}

export interface CreateOrderParams {
  deliveryType?: number
  payType?: number
  receiver: string
  receiverPhone: string
  address?: string
  remark?: string
}

/** 学生端 */
export function createOrder(data: CreateOrderParams) {
  return post<OrderVO>('/orders', data)
}

export function listMyOrders(params?: { status?: number; pageNum?: number; pageSize?: number }) {
  return get<PageResult<OrderVO>>('/orders', params)
}

export function getOrderDetail(id: string) {
  return get<OrderVO>(`/orders/${id}`)
}

/** 取消订单。reason 是后端 @RequestParam，走 query 参数 */
export function cancelOrder(id: string, reason?: string) {
  const q = reason ? `?reason=${encodeURIComponent(reason)}` : ''
  return post<void>(`/orders/${id}/cancel${q}`)
}

/** 商户端 */
export function listShopOrders(params?: { status?: number; pageNum?: number; pageSize?: number }) {
  return get<PageResult<OrderVO>>('/merchant/orders', params)
}

/**
 * 商户端经营概览（本店订单聚合指标）。
 * 返回 key 是后端 SQL 别名：totalOrders / todayOrders / todaySales /
 * pendingCount / preparingCount / pickingCount / deliveringCount /
 * finishedCount / totalSales
 */
export function getShopStats() {
  return get<Record<string, number>>('/merchant/orders/stats')
}

export function acceptOrder(id: string) {
  return post<Order>(`/merchant/orders/${id}/accept`)
}

export function readyOrder(id: string) {
  return post<Order>(`/merchant/orders/${id}/ready`)
}

/** 拒单。reason 是后端【必填】的 @RequestParam，不传会直接 400 */
export function rejectOrder(id: string, reason: string) {
  return post<Order>(`/merchant/orders/${id}/reject?reason=${encodeURIComponent(reason)}`)
}
