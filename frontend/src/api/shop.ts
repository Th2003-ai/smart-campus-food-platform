import { get, put } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 与后端 modules/shop/entity/Shop.java 字段保持一致 */
export interface Shop {
  id: string
  merchantId?: string
  categoryId?: string
  shopName: string
  logo?: string
  description?: string
  address?: string
  phone?: string
  notice?: string
  /** 营业时间，例如 09:00-21:30 */
  openTime?: string
  /** 0 休息中 / 1 营业中 */
  businessStatus?: number
  /** 0 正常 / 1 已封禁 */
  banStatus?: number
  /** 起送价（后端实体字段名就是 minPrice，不是 minAmount） */
  minPrice?: number
  deliveryFee?: number
  /** 预计送达时间（分钟） */
  deliveryTime?: number
  score?: number
  monthlySales?: number
}

/** 学生端：店铺列表 / 详情 */
export function listShops(params?: { keyword?: string; categoryId?: string; pageNum?: number; pageSize?: number }) {
  return get<PageResult<Shop>>('/shops', params)
}

export function getShopDetail(id: string) {
  return get<Shop>(`/shops/${id}`)
}

/** 商户端：我的店铺 */
export function getMyShop() {
  return get<Shop>('/merchant/shop')
}

export function updateMyShop(data: Partial<Shop>) {
  return put<void>('/merchant/shop', data)
}

/**
 * 营业状态：1 营业中 / 0 休息中
 * 注意：后端 MerchantShopController 用的是 @RequestParam，走 query 参数而不是请求体。
 */
export function updateBusinessStatus(businessStatus: number) {
  return put<void>(`/merchant/shop/status?businessStatus=${businessStatus}`)
}
