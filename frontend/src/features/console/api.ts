import { http } from '@/core/http/client'
import { getToken } from '@/core/utils/storage'

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
  params: string | null
  errorMsg: string | null
  costMs: number | null
  traceId: string | null
}

export interface ScheduleJob {
  code: string
  name: string
  description: string
  cronExpr: string
  enabled: boolean
  allowManual: boolean
  sortOrder: number
  lastRunAt: string | null
  lastStatus: string | null
  lastCostMs: number | null
  lastError: string | null
  nextRunAt: string | null
}

export interface JobLog {
  id: string
  jobName: string
  startedAt: string
  finishedAt: string | null
  success: boolean
  costMs: number | null
  message: string | null
  errorMsg: string | null
  triggerBy: string
}

export interface JobRunResult {
  jobCode: string
  success: boolean
  message: string
  costMs: number
  finishedAt: string
}

export interface BackupFile {
  fileName: string
  size: number
  sizeText: string
  triggerBy: string | null
  status: string
  createdAt: string
}

export interface RestoreResult {
  status: string
  message: string
}

export interface RestoreStatus {
  pending: boolean
  fileName: string | null
  queuedAt: string | null
  lastResult: string | null
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
  operLogs(page = 1, size = 20, keyword = '', result = '') {
    return http.get<PageResult<OperLog>>('/console/logs/oper', { page, size, keyword, result })
  },

  jobs() {
    return http.get<ScheduleJob[]>('/console/schedule/jobs')
  },
  updateJob(code: string, payload: { cronExpr?: string; enabled?: boolean }) {
    return http.patch<ScheduleJob[]>(`/console/schedule/jobs/${encodeURIComponent(code)}`, payload)
  },
  triggerJob(code: string) {
    return http.post<JobRunResult>(`/console/schedule/jobs/${encodeURIComponent(code)}/trigger`)
  },
  jobLogs(params: { jobCode?: string; page?: number; size?: number } = {}) {
    return http.get<PageResult<JobLog>>('/console/schedule/logs', params)
  },

  backups() {
    return http.get<BackupFile[]>('/console/backups')
  },
  createBackup() {
    return http.post<BackupFile>('/console/backups')
  },
  deleteBackup(fileName: string) {
    return http.delete<void>(`/console/backups/${encodeURIComponent(fileName)}`)
  },
  restoreStatus() {
    return http.get<RestoreStatus>('/console/backups/restore/status')
  },
}

const baseURL = import.meta.env.VITE_API_BASE || '/api'

/** 下载与上传走 fetch：需要 token 头，且响应不是统一 Result 包装 */
export async function downloadBackup(fileName: string): Promise<void> {
  const response = await fetch(`${baseURL}/console/backups/${encodeURIComponent(fileName)}/download`, {
    headers: { 'MiniDesk-Token': getToken() },
  })
  if (!response.ok) {
    throw new Error('备份文件下载失败')
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  URL.revokeObjectURL(url)
}

export async function uploadRestore(file: File): Promise<RestoreResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`${baseURL}/console/backups/restore`, {
    method: 'POST',
    headers: { 'MiniDesk-Token': getToken() },
    body: form,
  })
  const body = (await response.json()) as { code: number; message: string; data: RestoreResult }
  if (!response.ok || body.code !== 0) {
    throw new Error(body.message || '恢复包上传失败')
  }
  return body.data
}
