import { http } from '@/core/http/client'

export interface NoteSummary {
  id: string
  title: string
  module: 'work' | 'study' | 'life'
  type: string
  tags: string | null
  wordCount: number
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface NoteDetail extends NoteSummary {
  content: string
  folder: string | null
}

export interface NotePayload {
  title?: string
  content: string
  module?: string
  type?: string
  tags?: string | null
  pinned?: boolean
}

export interface NotePage {
  list: NoteSummary[]
  total: number
  page: number
  size: number
}

export const noteApi = {
  page(params: { module?: string; type?: string; keyword?: string; page?: number; size?: number }) {
    return http.get<NotePage>('/notes', params)
  },
  detail(id: string) {
    return http.get<NoteDetail>(`/notes/${id}`)
  },
  create(payload: NotePayload) {
    return http.post<NoteDetail>('/notes', payload)
  },
  update(id: string, payload: NotePayload) {
    return http.put<NoteDetail>(`/notes/${id}`, payload)
  },
  togglePin(id: string, pinned: boolean) {
    return http.patch<void>(`/notes/${id}/pin`, { pinned })
  },
  remove(id: string) {
    return http.delete<void>(`/notes/${id}`)
  },
}
