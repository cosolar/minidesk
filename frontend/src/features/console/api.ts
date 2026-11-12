import { http } from '@/core/http/client'

export interface ConsoleDashboard {
  totalUsers: number
  active7d: number
  disabledUsers: number
  tasks: number
  notes: number
  ledgers: number
  lifeLogs: number
  dbSizeMb: number
  uptimeHours: number
  partial: boolean
}

export interface ConsoleUser {
  id: string
  username: string
  nickname: string
  status: number
  roles: string[]
  source: string
  taskCount: number
  noteCount: number
  ledgerCount: number
  lastLoginAt: string | null
  createdAt: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface ConfigItem {
  key: string
  value: string
  remark: string
}

export interface LoginLog {
  id: string
  username: string
  success: boolean
  failReason: string | null
  ip: string | null
  createdAt: string
}

export interface OperLog {
  id: string
  username: string
  module: string
  action: string
  targetType: string | null
  targetId: string | null
  result: string
  createdAt: string
}

export const consoleApi = {
  dashboard() {
    return http.get<ConsoleDashboard>('/console/dashboard')
  },
  users(params: { keyword?: string; page?: number; size?: number }) {
    return http.get<PageResult<ConsoleUser>>('/console/users', params)
  },
  createUser(payload: { username: string; nickname?: string; password: string; roleCode?: string }) {
    return http.post<void>('/console/users', payload)
  },
  updateStatus(id: string, enabled: boolean) {
    return http.patch<void>(`/console/users/${id}/status`, { enabled })
  },
  resetPassword(id: string) {
    return http.post<{ tempPassword: string }>(`/console/users/${id}/reset-password`)
  },
  removeUser(id: string) {
    return http.delete<void>(`/console/users/${id}`)
  },
  config() {
    return http.get<ConfigItem[]>('/console/config')
  },
  updateConfig(values: Record<string, string>) {
    return http.put<ConfigItem[]>('/console/config', values)
  },
  loginLogs(page = 1, size = 20) {
    return http.get<PageResult<LoginLog>>('/console/logs/login', { page, size })
  },
  operLogs(page = 1, size = 20) {
    return http.get<PageResult<OperLog>>('/console/logs/oper', { page, size })
  },
}
