/**
 * 状态字典与格式化工具。
 *
 * 所有取值都以后端为准（见 database/schema.sql 字段注释与后端枚举），
 * 不要在页面里散落魔法数字。
 */

/** 订单状态：0 待接单 1 备餐中 2 待取餐 3 配送中 4 已完成 5 已取消 6 申诉中 */
export const ORDER_STATUS = {
  0: '待接单',
  1: '备餐中',
  2: '待取餐',
  3: '配送中',
  4: '已完成',
  5: '已取消',
  6: '申诉中'
} as const

export type OrderStatus = keyof typeof ORDER_STATUS

/** Element Plus tag 类型，按状态给颜色 */
export function orderStatusTagType(status?: number): 'success' | 'info' | 'warning' | 'danger' | 'primary' {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
    case 2:
      return 'primary'
    case 3:
      return 'primary'
    case 4:
      return 'success'
    case 5:
      return 'info'
    case 6:
      return 'danger'
    default:
      return 'info'
  }
}

export function orderStatusText(status?: number): string {
  return status === undefined || status === null ? '-' : ORDER_STATUS[status as OrderStatus] || '未知'
}

/** 配送方式：1 骑手配送 / 2 宿舍楼下自取 */
export function deliveryTypeText(type?: number): string {
  return type === 2 ? '宿舍楼下自取' : '骑手配送'
}

/** 支付状态：0 未支付 1 已支付 2 已退款 */
export function payStatusText(status?: number): string {
  if (status === 1) return '已支付'
  if (status === 2) return '已退款'
  return '未支付'
}

/** 订单可取消：仅「待接单」 */
export function canCancelOrder(status?: number): boolean {
  return status === 0
}

/** 订单可评价：仅「已完成」 */
export function canReviewOrder(status?: number): boolean {
  return status === 4
}

/**
 * 逗号分隔字符串 → 数组，用于 tags / ingredients / aiTags 展示
 */
export function splitCsv(text?: string): string[] {
  if (!text) return []
  return text
    .split(/[,，、;；]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 金额格式化：统一两位小数 */
export function money(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '0.00'
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

/**
 * 收货地址拼成一行文本，写入订单的 address 字段。
 * 后端地址是 campusArea / building / detail 三段。
 */
export function joinAddress(parts: {
  campusArea?: string
  building?: string
  detail?: string
}): string {
  return [parts.campusArea, parts.building, parts.detail].filter(Boolean).join(' ')
}
