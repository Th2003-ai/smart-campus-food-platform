import { get, put } from '@/utils/request'

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

export function updateUserRole(id: string, role: string) {
  return put<void>(`/admin/perm/users/${id}/role`, { role })
}
