import { defineStore } from 'pinia'

import { login as loginApi, logout as logoutApi, type LoginParams } from '@/api/auth'
import { PERMS_KEY, ROLE_KEY, TOKEN_KEY } from '@/utils/request'

/** 角色码，与后端 com.campus.delivery.security.RoleEnum 保持一致 */
export const ROLE = {
  STUDENT: 'STUDENT',
  MERCHANT: 'MERCHANT',
  RIDER: 'RIDER',
  ADMIN: 'ADMIN'
} as const

/** 角色默认首页 */
export const ROLE_HOME: Record<string, string> = {
  [ROLE.STUDENT]: '/student/home',
  [ROLE.MERCHANT]: '/merchant/dashboard',
  [ROLE.RIDER]: '/rider/hall',
  [ROLE.ADMIN]: '/admin/dashboard'
}

/** 管理员通配码，拥有它等于拥有一切权限（与后端 PermissionService.WILDCARD 一致） */
export const PERM_WILDCARD = 'admin:all'

function readPerms(): string[] {
  try {
    const raw = localStorage.getItem(PERMS_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? (parsed as string[]) : []
  } catch {
    return []
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    role: localStorage.getItem(ROLE_KEY) || '',
    userId: '',
    nickname: '',
    /** 当前角色被授予的权限码（登录时由后端下发） */
    perms: readPerms()
  }),

  getters: {
    isLogin: (state) => !!state.token,
    home: (state) => ROLE_HOME[state.role] || '/login',
    /** 是否拥有某个权限码（admin:all 通配） */
    hasPerm: (state) => (code?: string) => {
      if (!code) return true
      // 后端未下发 perms（老版本后端）时不做前端限制，交给后端 403 兜底
      if (state.perms.length === 0) return true
      if (state.perms.includes(PERM_WILDCARD)) return true
      return state.perms.includes(code)
    },
    /** 任意一个权限码即可 */
    hasAnyPerm: (state) => (codes: string[] = []) => {
      if (!codes.length) return true
      if (state.perms.length === 0) return true
      if (state.perms.includes(PERM_WILDCARD)) return true
      return codes.some((code) => state.perms.includes(code))
    }
  },

  actions: {
    async login(params: LoginParams) {
      const result = await loginApi(params)
      this.token = result.token
      this.role = result.role
      this.userId = result.userId
      this.nickname = result.nickname || ''
      this.perms = result.perms || []
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(ROLE_KEY, result.role)
      localStorage.setItem(PERMS_KEY, JSON.stringify(this.perms))
      return result
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        this.reset()
      }
    },

    reset() {
      this.token = ''
      this.role = ''
      this.userId = ''
      this.nickname = ''
      this.perms = []
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(ROLE_KEY)
      localStorage.removeItem(PERMS_KEY)
    }
  }
})
