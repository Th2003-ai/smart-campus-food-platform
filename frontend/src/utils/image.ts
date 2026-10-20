/**
 * 图片地址工具。
 *
 * 背景：本项目没有对象存储，演示数据里菜品 `image` 全为空。
 * 后端提供了 `GET /dishes/{id}/cover.svg`，会按菜名自动生成一张封面，
 * 因此前端统一走 {@link dishCover}：有真实图片用真实图片，没有就回退到自动封面。
 *
 * 这样商户新加的菜品也会自动有图，不需要手工上传。
 */

/** 与 axios 的 baseURL 保持一致，避免硬编码 /api */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'

interface DishLike {
  /** 菜品ID（Dish 用 id，购物车/订单明细用 dishId） */
  id?: string
  dishId?: string
  /** 真实图片（Dish 用 image，订单/购物车快照用 dishImage） */
  image?: string | null
  dishImage?: string | null
}

/**
 * 取菜品图片地址：真实图片优先，否则回退到后端自动生成的 SVG 封面。
 *
 * @returns 可直接给 el-image / img 的 src；菜品无 id 且无图时返回空串
 */
export function dishCover(dish?: DishLike | null): string {
  if (!dish) return ''
  const real = dish.image || dish.dishImage
  if (real) return real
  const id = dish.id || dish.dishId
  if (!id) return ''
  return `${API_BASE}/dishes/${id}/cover.svg`
}

/** 店铺 logo：没有就用一个本地占位（店铺暂无自动封面） */
export function shopLogo(logo?: string | null): string {
  return logo || ''
}
