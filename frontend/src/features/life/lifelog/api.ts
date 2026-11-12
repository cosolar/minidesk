import { http } from '@/core/http/client'

export interface LifeLog {
  id: string
  title: string
  content: string | null
  mood: string | null
  weather: string | null
  location: string | null
  logDate: string
  createdAt: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export const lifeLogApi = {
  page(month?: string, page = 1, size = 50) {
    return http.get<PageResult<LifeLog>>('/life-logs', { month, page, size })
  },
  create(payload: { title: string; content?: string; mood?: string; logDate?: string }) {
    return http.post<LifeLog>('/life-logs', payload)
  },
  update(id: string, payload: Record<string, unknown>) {
    return http.put<void>(`/life-logs/${id}`, payload)
  },
  remove(id: string) {
    return http.delete<void>(`/life-logs/${id}`)
  },
}
