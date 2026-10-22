import { del, get, post, put } from '@/utils/request'

/**
 * 购物车项（对应后端 CartItemVO，字段一个都不能少）
 *
 * 注意 amount 是后端算好的小计，不要前端用 price * quantity 自己算，
 * 否则和后端口径可能不一致。
 */
export interface CartItem {
  id: string
  shopId: string
  shopName?: string
  dishId: string
  dishName?: string
  dishImage?: string
  specId?: string
  specName?: string
  price?: number
  quantity: number
  selected?: number
  /** 小计金额 = price * quantity（后端计算） */
  amount?: number
  /** 当前库存 */
  stock?: number
  /** 菜品是否仍上架：0 下架 1 上架 */
  dishStatus?: number
}

export interface AddCartParams {
  dishId: string
  quantity: number
}

export function listCart() {
  return get<CartItem[]>('/cart')
}

export function addToCart(data: AddCartParams) {
  return post<void>('/cart', data)
}

/**
 * 调整数量。
 * 注意：后端 CartController 用的是 @RequestParam，必须走 query 参数。
 * 之前写成 body { quantity } 会被后端判为「缺少必要参数：quantity」。
 */
export function updateQuantity(id: string, quantity: number) {
  return put<void>(`/cart/${id}/quantity?quantity=${quantity}`)
}

/** 勾选/取消勾选。同样必须走 query 参数（后端 @RequestParam Integer selected） */
export function updateSelected(id: string, selected: number) {
  return put<void>(`/cart/${id}/selected?selected=${selected}`)
}

export function removeCartItem(id: string) {
  return del<void>(`/cart/${id}`)
}

export function clearCart() {
  return del<void>('/cart')
}
