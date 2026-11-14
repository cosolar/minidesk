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

/** 笔记当前分享配置；未分享时接口返回 null */
export interface NoteShare {
  noteId: string
  token: string
  hasPassword: boolean
  enabled: boolean
  expireAt: string | null
  updatedAt: string
}

export interface NoteSharePayload {
  token: string
  /** 省略 = 保持原密码；空串 = 清除；非空 = 设置新密码 */
  password?: string
  clearPassword?: boolean
  expireAt?: string | null
  enabled?: boolean
}

export type PublicNoteStatus = 'ok' | 'password_required' | 'wrong_password' | 'expired' | 'not_found'

export interface PublicNote {
  status: PublicNoteStatus
  title: string | null
  content: string | null
  updatedAt: string | null
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
  getShare(id: string) {
    return http.get<NoteShare | null>(`/notes/${id}/share`)
  },
  saveShare(id: string, payload: NoteSharePayload) {
    return http.put<NoteShare>(`/notes/${id}/share`, payload)
  },
  cancelShare(id: string) {
    return http.delete<void>(`/notes/${id}/share`)
  },
}

export const noteShareApi = {
  view(token: string) {
    return http.get<PublicNote>(`/share/note/${encodeURIComponent(token)}`)
  },
  unlock(token: string, password: string) {
    return http.post<PublicNote>(`/share/note/${encodeURIComponent(token)}/unlock`, { password })
  },
}
