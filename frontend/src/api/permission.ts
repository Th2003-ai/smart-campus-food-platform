import { get, post, put } from '@/utils/request'

/** 一条授权项 */
export interface RolePermItem {
  permission: string
  dataScope: string
}

/** 权限矩阵：四个主体 × 权限码 */
export interface PermMatrix {
  roles: string[]
  scopes: string[]
  perms: string[]
  granted: Record<string, Record<string, string>>
}

/** 成员行（不含密码） */
export interface SysUserRow {
  id: string
  username: string
  nickname?: string
  role: string
  phone?: string
  status?: number
  lastLoginTime?: string
}

/** 新增成员入参 */
export interface CreateUserParams {
  username: string
  nickname?: string
  phone?: string
  role: string
  /** 不填则使用默认密码 Campus@2026 */
  password?: string
}

/** 新增成员的返回 */
export interface CreateUserResult {
  userId: string
  username: string
  role: string
  /** 用了默认密码时后端会回显，方便现场告知对方 */
  defaultPassword?: string | null
}

export function permMatrix() {
  return get<PermMatrix>('/admin/perm/matrix')
}

/** 整体覆盖某个角色的授权 */
export function saveRolePerms(role: string, items: RolePermItem[]) {
  return put<void>(`/admin/perm/roles/${role}`, items)
}

export function permUsers() {
  return get<SysUserRow[]>('/admin/perm/users')
}

/** 新增成员 */
export function createPermUser(data: CreateUserParams) {
  return post<CreateUserResult>('/admin/perm/users', data)
}

export function updateUserRole(id: string, role: string) {
  return put<void>(`/admin/perm/users/${id}/role`, { role })
}

/** 启用 / 停用（0 停用 1 启用） */
export function updateUserStatus(id: string, status: number) {
  return put<void>(`/admin/perm/users/${id}/status`, { status })
}

/** 重置密码 */
export function resetUserPassword(id: string, password: string) {
  return put<void>(`/admin/perm/users/${id}/password`, { password })
}
