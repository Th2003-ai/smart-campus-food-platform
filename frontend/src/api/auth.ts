import { post } from '@/utils/request'

/** 登录入参（对应后端 LoginRequest） */
export interface LoginParams {
  username: string
  password: string
}

/** 登录返回（对应后端 LoginResponse） */
export interface LoginResult {
  token: string
  userId: string
  role: string
  nickname?: string
  avatar?: string
}

/**
 * 注册角色。
 * 管理员不开放注册（后端会拒绝），只能使用系统预置账号。
 */
export type RegisterRole = 'STUDENT' | 'MERCHANT' | 'RIDER'

/**
 * 注册入参（必须与后端 RegisterRequest 字段一一对应）
 *
 * 登录账号来源不同：
 *   学生 → studentNo（学号）
 *   商户 / 骑手 → phone（手机号）
 *
 * 三种角色的必填项不同，因此除公共字段外其余都是可选的，
 * 由页面按角色收集、后端按角色校验。
 */
export interface RegisterParams {
  role: RegisterRole
  password: string
  /** 手机号：商户/骑手同时作为登录账号 */
  phone: string

  // ---- 学生 ----
  /** 学号（学生必填，作为登录账号） */
  studentNo?: string
  /** 真实姓名（学生、骑手必填） */
  realName?: string
  college?: string
  grade?: string

  // ---- 商户 ----
  /** 商户名称（商户必填） */
  merchantName?: string
  contactName?: string
  /** 营业执照 / 经营许可编号 */
  licenseNo?: string

  // ---- 骑手 ----
  studentCardNo?: string
  /** 步行 / 自行车 / 电动车 */
  vehicleType?: string
}

/** 登录 */
export function login(data: LoginParams) {
  return post<LoginResult>('/auth/login', data)
}

/** 注册 */
export function register(data: RegisterParams) {
  return post<void>('/auth/register', data)
}

/** 退出登录 */
export function logout() {
  return post<void>('/auth/logout')
}
