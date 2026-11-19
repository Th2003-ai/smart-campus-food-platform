import { del, get, post, put } from '@/utils/request'

/** 角色（对应后端 sys_role 一行） */
export interface RoleItem {
  code: string
  name: string
  description?: string
  sort: number
  /** 内置角色（四端入口用，不可删除） */
  builtin: boolean
  /** 1 启用 / 0 停用 */
  status: number
  /** 1 允许自助注册 */
  allowRegister: number
  /** 所属端：STUDENT/MERCHANT/RIDER/ADMIN，决定可用页面与接口 */
  portal?: string | null
  /** 登录后首页，如 /admin/dashboard */
  homePath?: string | null
  userCount?: number
}

/** 新增/修改角色的入参 */
export interface RoleForm {
  code?: string
  name: string
  description?: string
  sort?: number
  status?: number
  allowRegister?: number
  portal?: string | null
  homePath?: string | null
}

/** 管理端：角色列表 */
export function listRoles() {
  return get<RoleItem[]>('/admin/roles')
}

/** 管理端：新增角色 */
export function createRole(data: RoleForm) {
  return post<RoleItem>('/admin/roles', data)
}

/** 管理端：修改角色 */
export function updateRole(code: string, data: RoleForm) {
  return put<RoleItem>(`/admin/roles/${code}`, data)
}

/** 管理端：删除角色（内置角色、仍被用户使用的角色会被拒绝） */
export function deleteRole(code: string) {
  return del<void>(`/admin/roles/${code}`)
}

/** 公开：可注册角色（注册页下拉用） */
export function listRegisterableRoles() {
  return get<Array<{ code: string; name: string }>>('/auth/roles', { scope: 'register' })
}

/** 公开：全部启用角色（登录页入口用，含不可注册的角色如管理员） */
export function listLoginRoles() {
  return get<Array<{ code: string; name: string }>>('/auth/roles', { scope: 'login' })
}
