import { http } from '@/core/http/client'

export interface MeetingAction {
  id: number
  content: string
  owner: string | null
  dueDate: string | null
  taskId: number | null
  status: string
}

export interface MeetingVO {
  id: number
  title: string
  meetingDate: string
  startTime: string | null
  endTime: string | null
  location: string | null
  host: string | null
  content: string | null
  projectId: number | null
  attendees: string[]
  actions: MeetingAction[]
  createdAt: string | null
}

export interface MeetingPayload {
  title: string
  meetingDate: string
  startTime?: string | null
  endTime?: string | null
  location?: string | null
  host?: string | null
  content?: string | null
  attendees?: string[]
  actions?: Array<{ content: string; owner?: string | null; dueDate?: string | null }>
}

export const meetingApi = {
  list(keyword?: string) {
    return http.get<MeetingVO[]>('/meetings', { keyword })
  },
  detail(id: number) {
    return http.get<MeetingVO>(`/meetings/${id}`)
  },
  create(payload: MeetingPayload) {
    return http.post<MeetingVO>('/meetings', payload)
  },
  update(id: number, payload: MeetingPayload) {
    return http.put<MeetingVO>(`/meetings/${id}`, payload)
  },
  remove(id: number) {
    return http.delete<void>(`/meetings/${id}`)
  },
  actionToTask(actionId: number) {
    return http.post<number>(`/meetings/actions/${actionId}/task`)
  },
}
