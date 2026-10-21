import { del, get, post, put } from '@/utils/request'

/** 个人中心（对应后端 UserProfileVO 真实字段，手机号已脱敏） */
export interface UserProfile {
  userId: string
  username: string
  nickname?: string
  /** 已脱敏，形如 138****0001 */
  phone?: string
  avatar?: string
  role?: string
  /** 学号（学生才有） */
  studentNo?: string
  college?: string
  grade?: string
  /** 0 普通 1 减脂 2 增肌 */
  dietGoal?: number
  tastePreference?: string
  allergyFoods?: string
  /** 账户余额（仅学生；余额支付用，金额只以数据库为准） */
  balance?: number
}

/**
 * 饮食档案（对应后端 DietProfileDTO）
 *
 * 注意：后端没有 dailyBudget 字段，身高体重是 heightCm / weightKg，
 * 这两个是营养估算的输入，之前漏了会影响 AI 饮食分析。
 */
export interface DietProfile {
  /** 0 普通 1 减脂 2 增肌 */
  dietGoal?: number
  /** 口味偏好，逗号分隔，如 "清淡,微辣" */
  tastePreference?: string
  /** 忌口/过敏原，逗号分隔，AI 点餐会过滤 */
  allergyFoods?: string
  dislikeFoods?: string
  heightCm?: number
  weightKg?: number
}

/**
 * 收货地址（对应后端 AddressDTO / UserAddress）
 *
 * 注意：不是单个 address 字段，而是 campusArea + building + detail 三段，
 * 其中 detail 是必填。只传 address 会导致「详细地址不能为空」。
 */
export interface UserAddress {
  /** 修改时必填，新增不传 */
  id?: string
  receiver: string
  phone: string
  /** 校区/园区 */
  campusArea?: string
  /** 宿舍楼/教学楼 */
  building?: string
  /** 详细地址（必填） */
  detail: string
  /** 是否设为默认 */
  isDefault?: boolean
}

export function getProfile() {
  return get<UserProfile>('/user/profile')
}

export function getDietProfile() {
  return get<DietProfile>('/user/diet-profile')
}

export function saveDietProfile(data: DietProfile) {
  return put<void>('/user/diet-profile', data)
}

export function listAddress() {
  return get<UserAddress[]>('/user/address')
}

export function addAddress(data: UserAddress) {
  return post<void>('/user/address', data)
}

export function removeAddress(id: string) {
  return del<void>(`/user/address/${id}`)
}
