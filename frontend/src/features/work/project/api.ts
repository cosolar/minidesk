import { http } from '@/core/http/client'

export interface ProjectVO {
  id: string
  name: string
  description: string | null
  color: string | null
  status: string
  priority: number | null
  startDate: string | null
  endDate: string | null
  progress: number
  stats: { total: number; done: number; doing: number; overdue: number }
}

export interface ProjectPayload {
  name: string
  description?: string | null
  color?: string | null
  status?: string
  priority?: number
  startDate?: string | null
  endDate?: string | null
}

export const projectApi = {
  list(params: { status?: string; keyword?: string } = {}) {
    return http.get<ProjectVO[]>('/projects', params)
  },
  detail(id: string) {
    return http.get<ProjectVO>(`/projects/${id}`)
  },
  create(payload: ProjectPayload) {
    return http.post<ProjectVO>('/projects', payload)
  },
  update(id: string, payload: ProjectPayload) {
    return http.put<ProjectVO>(`/projects/${id}`, payload)
  },
  remove(id: string) {
    return http.delete<void>(`/projects/${id}`)
  },
}
