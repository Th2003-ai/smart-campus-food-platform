import { del, get, post, put } from '@/utils/request'

/** 与后端 modules/dish/entity/Dish.java 保持一致 */
export interface Dish {
  id: string
  shopId?: string
  categoryId?: string
  dishName: string
  description?: string
  image?: string
  price: number
  stock?: number
  monthlySales?: number
  score?: number
  /** 菜品标签，逗号分隔，如 "清淡,低脂,不辣" —— AI 语义搜索、个性化推荐要用 */
  tags?: string
  /** 主要食材，逗号分隔 —— 忌口/过敏原过滤的依据 */
  ingredients?: string
  /** 0 下架 / 1 上架（后端实体字段名就是 status，不是 shelfStatus） */
  status?: number
  createTime?: string
  updateTime?: string
}

/** 与后端 modules/dish/entity/DishCategory.java 保持一致 */
export interface DishCategory {
  id: string
  shopId?: string
  name: string
  sort?: number
  /** 0 停用 / 1 启用 */
  status?: number
}

/** 学生端：菜品详情 */
export function getDishDetail(id: string) {
  return get<Dish>(`/dishes/${id}`)
}

/** 学生端：关键词检索（后端返回的是 List，没有分页，条数用 limit 控制） */
export function searchDishes(params: { keyword: string; shopId?: string; limit?: number }) {
  return get<Dish[]>('/dishes/search', params)
}

/** 商户端：本店菜品列表（后端返回 List，不分页；可按分类、上下架状态筛选） */
export function listMyDishes(params?: { categoryId?: string; status?: number }) {
  return get<Dish[]>('/merchant/dishes', params)
}

/** 商户端：新增或修改菜品（带 id 是修改，不带 id 是新增） */
export function saveDish(data: Partial<Dish>) {
  return post<Dish>('/merchant/dishes', data)
}

/** 商户端：上下架 0 下架 / 1 上架（后端是 query 参数 status） */
export function updateDishStatus(id: string, status: number) {
  return put<void>(`/merchant/dishes/${id}/status?status=${status}`)
}

/** 商户端：调整库存（后端是 query 参数 stock；改成 0 会自动下架） */
export function updateDishStock(id: string, stock: number) {
  return put<void>(`/merchant/dishes/${id}/stock?stock=${stock}`)
}

/** 商户端：本店菜品分类（后端要求必须传 shopId） */
export function listDishCategories(shopId: string) {
  return get<DishCategory[]>('/merchant/dish-categories', { shopId })
}

/**
 * 注意：后端 MerchantDishController 目前【没有】删除菜品的接口，调用会 404。
 * 保留它只是备用；要真做删除，得先让后端加接口（dish 表有 deleted 字段，可做逻辑删除）。
 */
export function removeDish(id: string) {
  return del<void>(`/merchant/dishes/${id}`)
}
