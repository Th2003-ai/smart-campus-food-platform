import { del, get, post, put } from '@/utils/request'

/** 一个用户组 */
export interface GroupRow {
  id: string
  code: string
  name: string
  builtin: number
  remark?: string | null
}

/** 权限矩阵：组 -> 已授权 [module.method] */
export interface GroupMatrix {
  groups: GroupRow[]
  granted: Record<string, string[]>
  scopes: Record<string, Record<string, string>>
  scopesAllowed: string[]
}

/** 代码里真实存在的接口：模块 -> 方法 */
export interface EndpointTree {
  modules: Array<{
    module: string
    methods: Array<{ method: string; key: string; paths: string[] }>
  }>
  total: number
}

export interface GroupUserRow {
  id: string
  username: string
  nickname?: string
  role: string
  status?: number
  groupIds: string[]
}

export const groupMatrix = () => get<GroupMatrix>('/admin/groups/matrix')

export const groupEndpoints = () => get<EndpointTree>('/admin/groups/endpoints')

export const saveGroupPrivs = (
  groupId: string,
  items: Array<{ module: string; method: string; dataScope: string }>
) => put<void>(`/admin/groups/${groupId}/privs`, items)

export const createGroup = (data: { name: string; code?: string; remark?: string }) =>
  post<GroupRow>('/admin/groups', data)

export const deleteGroup = (groupId: string) => del<void>(`/admin/groups/${groupId}`)

export const groupUsers = () => get<GroupUserRow[]>('/admin/groups/users')

export const setUserGroups = (userId: string, groupIds: string[]) =>
  put<void>(`/admin/groups/users/${userId}`, groupIds)
