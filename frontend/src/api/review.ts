import { get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/**
 * 评价实体（对应后端 Review 实体真实字段）
 *
 * 注意：后端没有 score / anonymous / reply 这三个字段，
 * 实际是 tasteScore / isAnonymous / merchantReply —— 写错的话页面会显示空白。
 */
export interface Review {
  id: string
  orderId?: string
  shopId?: string
  userId?: string
  riderId?: string
  /** 口味评分 1-5 */
  tasteScore?: number
  /** 商家服务评分 1-5 */
  serviceScore?: number
  /** 配送评分 1-5 */
  deliveryScore?: number
  content?: string
  /** 0 否 1 是 */
  isAnonymous?: number
  /** AI 提取的标签，逗号分隔 */
  aiTags?: string
  /** 情感：0 差评 1 中评 2 好评 */
  sentiment?: number
  /** 商家回复 */
  merchantReply?: string
  replyTime?: string
  createTime?: string
}

/**
 * 提交评价入参（必须与后端 ReviewSubmitDTO 一致）
 *
 * 只有 tasteScore 是必填（后端 review.taste_score NOT NULL）；
 * 以前写的 score / anonymous 后端收不到，会直接报「口味评分不能为空」。
 */
export interface ReviewSubmitParams {
  orderId: string
  /** 口味评分 1-5（必填） */
  tasteScore: number
  /** 商家服务评分 1-5（选填） */
  serviceScore?: number
  /** 配送评分 1-5（选填） */
  deliveryScore?: number
  content?: string
  /** 是否匿名 */
  anonymous?: boolean
  /** 图片 URL 列表（后端是 List<String>，不是逗号字符串） */
  images?: string[]
}

/** 学生端：提交评价 */
export function submitReview(data: ReviewSubmitParams) {
  return post<Review>('/reviews', data)
}

/** 公开：按店铺查评价（shopId 必填） */
export function listShopReviews(params: { shopId: string; pageNum?: number; pageSize?: number }) {
  return get<PageResult<Review>>('/reviews', params)
}

/** 商户端：本店评价列表，可按情感倾向筛选 0差评 1中评 2好评 */
export function listMyShopReviews(params?: {
  sentiment?: number
  pageNum?: number
  pageSize?: number
}) {
  return get<PageResult<Review>>('/merchant/reviews', params)
}

/** 商户回复。content 是后端 @RequestParam，必须走 query 参数 */
export function replyReview(id: string, content: string) {
  return post<void>(`/merchant/reviews/${id}/reply?content=${encodeURIComponent(content)}`)
}

/** 标签聚合项：后端 statTags 返回 tagName / sentiment / tagCount */
export interface ReviewTag {
  tagName: string
  /** 0 差评 1 中评 2 好评 */
  sentiment?: number
  tagCount: number
}

/** 标签聚合：用于商户端标签云 */
export function reviewTags(limit = 20) {
  return get<ReviewTag[]>('/merchant/reviews/tags', { limit })
}

/** 评价概览：reviewCount / avgTasteScore / avgServiceScore / avgDeliveryScore / goodRate */
export function reviewOverview() {
  return get<Record<string, unknown>>('/merchant/reviews/overview')
}
