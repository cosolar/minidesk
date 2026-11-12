import { http } from '@/core/http/client'

export interface TaskVO {
  id: string
  title: string
  note: string | null
  module: 'work' | 'study' | 'life'
  taskType: string
  priority: number
  status: 'todo' | 'doing' | 'done' | 'cancelled'
  startDate: string | null
  startTime: string | null
  dueDate: string | null
  dueTime: string | null
  projectId: string | null
  projectName: string | null
  tags: string | null
  done: boolean
  doneAt: string | null
  createdAt: string
}

export interface TaskPayload {
  title: string
  note?: string | null
  module?: string
  taskType?: string
  priority?: number
  status?: string
  startDate?: string | null
  startTime?: string | null
  dueDate?: string | null
  dueTime?: string | null
  projectId?: string | null
  planId?: string | null
  repeatRule?: string | null
  tags?: string | null
  estimateMinutes?: number | null
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface TaskQuery {
  module?: string
  status?: string
  keyword?: string
  date?: string
  projectId?: string
  page?: number
  size?: number
}

export const taskApi = {
  page(query: TaskQuery = {}) {
    return http.get<PageResult<TaskVO>>('/tasks', query)
  },
  today() {
    return http.get<TaskVO[]>('/tasks/today')
  },
  create(payload: TaskPayload) {
    return http.post<TaskVO>('/tasks', payload)
  },
  update(id: string, payload: TaskPayload) {
    return http.put<TaskVO>(`/tasks/${id}`, payload)
  },
  toggleDone(id: string, done: boolean) {
    return http.patch<TaskVO>(`/tasks/${id}/done`, { done })
  },
  remove(id: string) {
    return http.delete<void>(`/tasks/${id}`)
  },
}
