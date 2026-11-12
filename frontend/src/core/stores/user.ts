import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { cacheAccount, clearToken, getToken, setToken } from '@/core/utils/storage'

export interface UserProfile {
  id: string
  username: string
  nickname: string
  avatarUrl?: string | null
  email?: string | null
}

/** 登录 / 注册接口返回的会话载荷，见接口清单 §2 */
export interface SessionPayload {
  tokenName: string
  tokenValue: string
  expiresIn: number
  user: UserProfile
  roles: string[]
  permissions: string[]
  isFirstUser?: boolean
}

export const ROLE_ADMIN = 'ADMIN'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref<UserProfile | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => roles.value.includes(ROLE_ADMIN))
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '朋友')

  const initials = computed(() => {
    const name = displayName.value
    return name.slice(0, 1).toUpperCase()
  })

  function applySession(payload: SessionPayload): void {
    token.value = payload.tokenValue
    setToken(payload.tokenValue)
    profile.value = payload.user
    roles.value = payload.roles
    permissions.value = payload.permissions
    cacheAccount({
      userId: payload.user.id,
      username: payload.user.username,
      nickname: payload.user.nickname,
      avatarUrl: payload.user.avatarUrl,
    })
  }

  function applyProfile(user: UserProfile): void {
    profile.value = user
  }

  /** 刷新页面后恢复：token 还在，但内存中的用户态与权限需要重新回填 */
  function applySessionInfo(user: UserProfile, userRoles: string[], userPermissions: string[]): void {
    profile.value = user
    roles.value = userRoles
    permissions.value = userPermissions
  }

  function clearSession(): void {
    token.value = ''
    profile.value = null
    roles.value = []
    permissions.value = []
    clearToken()
  }

  /** 权限码支持 * 与 前缀通配（work:*） */
  function hasPermission(code: string): boolean {
    if (!code) {
      return true
    }
    return permissions.value.some((item) => {
      if (item === '*') {
        return true
      }
      if (item.endsWith(':*')) {
        return code.startsWith(item.slice(0, -1))
      }
      return item === code
    })
  }

  return {
    token,
    profile,
    roles,
    permissions,
    isLoggedIn,
    isAdmin,
    displayName,
    initials,
    applySession,
    applyProfile,
    applySessionInfo,
    clearSession,
    hasPermission,
  }
})
