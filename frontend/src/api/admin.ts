import { del, get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface Knowledge {
  id?: string
  category?: string
  question: string
  answer: string
  keywords?: string
  hitCount?: number
}

export interface Ticket {
  id: string
  ticketNo: string
  userId?: string
  sessionId?: string
  orderId?: string
  category?: string
  question: string
  status: number
  handlerId?: string
  reply?: string
  createTime?: string
  closeTime?: string
}

/** 数据看板 */
export function dashboardOverview() {
  return get<Record<string, number>>('/admin/dashboard/overview')
}

export function dashboardTrend(days = 7) {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/trend', { days })
}

export function dashboardShopRanking(limit = 10) {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/shop-ranking', { limit })
}

export function dashboardDishRanking(limit = 10) {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/dish-ranking', { limit })
}

export function dashboardCategoryShare() {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/category-share')
}

export function dashboardHourDistribution() {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/hour-distribution')
}

export function dashboardTagCloud(limit = 30) {
  return get<Array<Record<string, unknown>>>('/admin/dashboard/tag-cloud', { limit })
}

/** 审核列表 */
export interface MerchantAuditItem {
  id: string
  merchantName: string
  contactName?: string
  contactPhone?: string
  licenseNo?: string
  /** 0待审核 1已通过 2已驳回 */
  auditStatus: number
  auditRemark?: string
  auditTime?: string
  createTime?: string
  userId?: string
  /** 账号状态：0禁用 1正常（null 表示账号已不存在） */
  accountStatus?: number | null
}

export interface RiderAuditItem {
  id: string
  realName: string
  studentCardNo?: string
  vehicleType?: string
  /** 0休息中 1接单中 */
  workStatus?: number
  deliveringCount?: number
  totalIncome?: number
  /** 0待审核 1已通过 2已驳回 */
  auditStatus: number
  auditRemark?: string
  createTime?: string
  userId?: string
  accountStatus?: number | null
}

export interface ShopAuditItem {
  id: string
  shopName: string
  merchantId: string
  /** 0休息中 1营业中 */
  businessStatus?: number
  /** 0正常 1已封禁 */
  banStatus: number
  monthlySales?: number
  score?: number
  createTime?: string
}

export function listMerchantsForAudit(auditStatus?: number) {
  return get<MerchantAuditItem[]>('/admin/audit/merchants', { auditStatus })
}

export function listRidersForAudit(auditStatus?: number) {
  return get<RiderAuditItem[]>('/admin/audit/riders', { auditStatus })
}

export function listShopsForAudit(banStatus?: number) {
  return get<ShopAuditItem[]>('/admin/audit/shops', { banStatus })
}

/** 审核与处置 */
export function auditMerchant(id: string, pass: boolean, remark?: string) {
  return post<void>(`/admin/audit/merchant/${id}`, null, { params: { pass, remark } })
}

export function auditRider(id: string, pass: boolean, remark?: string) {
  return post<void>(`/admin/audit/rider/${id}`, null, { params: { pass, remark } })
}

export function banShop(id: string, ban: boolean) {
  return post<void>(`/admin/audit/shop/${id}/ban`, null, { params: { ban } })
}

export function banRider(id: string, ban: boolean) {
  return post<void>(`/admin/audit/rider/${id}/ban`, null, { params: { ban } })
}

/** 知识库 */
export function listKnowledge(category?: string) {
  return get<Knowledge[]>('/admin/knowledge', { category })
}

export function saveKnowledge(data: Knowledge) {
  return post<void>('/admin/knowledge', data)
}

export function removeKnowledge(id: string) {
  return del<void>(`/admin/knowledge/${id}`)
}

/** 工单 */
export function listTickets(params?: { status?: number; pageNum?: number; pageSize?: number }) {
  return get<PageResult<Ticket>>('/tickets', params)
}

export function handleTicket(id: string, reply: string, close = false) {
  return post<void>(`/tickets/${id}/handle`, null, { params: { reply, close } })
}

/** 菜单权限树（树形数据结构可视化） */
export interface MenuNode {
  id: string
  parentId: string
  label: string
  menuType: number
  typeText: string
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
  children?: MenuNode[]
}

/** 菜单平铺记录（数据库 sys_menu 原始行） */
export interface MenuFlat {
  id: string
  parentId: string
  menuName: string
  menuType: number
  path?: string
  component?: string
  perms?: string
  icon?: string
  sort?: number
}

/** 菜单树形结构（目录→菜单→按钮） */
export function menuTree() {
  return get<MenuNode[]>('/admin/menu/tree')
}

/** 菜单平铺列表 */
export function menuList() {
  return get<MenuFlat[]>('/admin/menu/list')
}
