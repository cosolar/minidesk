import { http } from '@/core/http/client'
import type { SessionPayload, UserProfile } from '@/core/stores/user'

export interface SessionInfo {
  user: UserProfile
  roles: string[]
  permissions: string[]
}

export interface RegisterConfig {
  allowRegister: boolean
  isFirstUser: boolean
  needClaimToken: boolean
  emailEnabled: boolean
  passwordPolicy: {
    minLength: number
    requireLetter: boolean
    requireDigit: boolean
  }
}

export interface EmailRegisterPayload {
  email: string
  code: string
  nickname?: string
  password: string
  confirmPassword: string
}

export interface RegisterPayload {
  username: string
  nickname?: string
  password: string
  confirmPassword: string
  claimToken?: string | null
}

/**
 * 认证接口，见 docs/接口清单.md §2。
 */
export const authApi = {
  login(username: string, password: string) {
    return http.post<SessionPayload>('/auth/login', { username, password })
  },
  register(payload: RegisterPayload) {
    return http.post<SessionPayload>('/auth/register', payload)
  },
  sendEmailCode(email: string) {
    return http.post<void>('/auth/email-code', { email })
  },
  registerByEmail(payload: EmailRegisterPayload) {
    return http.post<SessionPayload>('/auth/register-email', payload)
  },
  registerConfig() {
    return http.get<RegisterConfig>('/auth/register-config')
  },
  logout() {
    return http.post<void>('/auth/logout')
  },
  session() {
    return http.get<SessionInfo>('/user/session')
  },
}
