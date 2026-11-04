import { get, post } from '@/utils/request'

/** 字典概览：每张表的行数与加载时间 */
export interface DictSnapshot {
  tables: Record<string, number>
  totalRows: number
  loadedAt: number
  tableCount: number
  preloadEnabled: boolean
}

export interface HotKeyItem {
  key: string
  count: number
}

export interface HotKeyResult {
  topN: HotKeyItem[]
  totalAccess: number
  keyCount: number
  windowStart: number
}

export interface CacheStatus {
  dicts: DictSnapshot
  dictTables: string[]
  hotKeys: HotKeyItem[]
  hotKeyTotal: number
  nullValueCacheEnabled: boolean
}

export interface DictCompare {
  table: string
  rows: number
  cacheRows: number
  dbCostMs: number
  cacheCostMs: number
  rounds: number
  note: string
}

/** 公开：字典概览 */
export function getDicts() {
  return get<DictSnapshot>('/dicts')
}

/** 公开：某张字典表明细 */
export function getDictDetail(table: string) {
  return get<Record<string, unknown>[]>(`/dicts/${table}`)
}

/** 管理端：缓存总览 */
export function getCacheStatus() {
  return get<CacheStatus>('/admin/cache/status')
}

/** 管理端：热 key TopN */
export function getHotKeys(topN = 10) {
  return get<HotKeyResult>('/admin/cache/hotkeys', { topN })
}

/** 管理端：重置热 key 统计 */
export function resetHotKeys() {
  return post<void>('/admin/cache/hotkeys/reset')
}

/** 管理端：刷新字典缓存 */
export function refreshDicts() {
  return post<{ costMs: number; snapshot: DictSnapshot }>('/admin/cache/dicts/refresh')
}

/** 管理端：字典缓存 vs 直查库 */
export function compareDict(table: string) {
  return get<DictCompare>('/admin/cache/dicts/compare', { table })
}

/** 管理端：切换防穿透（空值缓存）开关 */
export function switchNullCache(enabled: boolean) {
  return post<{ nullValueCache: boolean }>(`/admin/cache/switch/null-cache?enabled=${enabled}`)
}

/** 演示用：并发打 N 次菜品详情（制造热点） */
export function burstDishDetail(dishId: string, times: number) {
  return Promise.all(Array.from({ length: times }, () => get(`/dishes/${dishId}`)))
}
