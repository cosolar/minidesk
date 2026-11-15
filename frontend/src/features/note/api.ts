import { http } from '@/core/http/client'

export type NoteModule = 'work' | 'study' | 'life'

export interface NoteSummary {
  id: string
  title: string
  /** 列表卡片摘要：后端剥掉 Markdown 后的纯文本，可能为空 */
  excerpt?: string
  module: NoteModule
  type: string
  tags: string | null
  /** 所属目录，null 表示直接挂在板块根目录 */
  folderId?: string | null
  wordCount: number
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface NoteDetail extends NoteSummary {
  content: string
  folder: string | null
}

/** 侧栏文档树节点：只有标题与归属，不含正文 */
export interface NoteOutline {
  id: string
  title: string
  module: NoteModule
  folderId: string | null
  wordCount: number
  pinned: boolean
  updatedAt: string
}

export interface NotePayload {
  title?: string
  content: string
  module?: string
  type?: string
  tags?: string | null
  pinned?: boolean
  folderId?: string | null
}

/** 笔记目录：工作 / 学习 / 生活是前端固定根，这里都是用户自建目录 */
export interface NoteFolder {
  id: string
  name: string
  module: NoteModule
  parentId: string | null
}

export interface NoteFolderPayload {
  name: string
  module?: string
  parentId?: string | null
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

export interface NoteRecycleItem {
  id: string
  title: string
  excerpt: string
  module: 'work' | 'study' | 'life'
  wordCount: number
  deletedAt: string | null
  /** 距离自动彻底删除还剩几天，由后端按 note_recycle_days 计算 */
  remainingDays: number
}

export interface NoteRecycleBoard {
  /** 后台配置的保留天数，用于文案提示 */
  keepDays: number
  items: NoteRecycleItem[]
}

export const noteApi = {
  page(params: { module?: string; type?: string; keyword?: string; page?: number; size?: number }) {
    return http.get<NotePage>('/notes', params)
  },
  outline() {
    return http.get<NoteOutline[]>('/notes/outline')
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
  /** 移动到目录；folderId 为空表示移到板块根目录 */
  move(id: string, payload: { folderId: string | null; module?: string }) {
    return http.patch<void>(`/notes/${id}/move`, payload)
  },
  togglePin(id: string, pinned: boolean) {
    return http.patch<void>(`/notes/${id}/pin`, { pinned })
  },
  remove(id: string) {
    return http.delete<void>(`/notes/${id}`)
  },
  recycle() {
    return http.get<NoteRecycleBoard>('/notes/recycle')
  },
  restore(id: string) {
    return http.post<void>(`/notes/${id}/restore`, {})
  },
  purge(id: string) {
    return http.delete<void>(`/notes/${id}/purge`)
  },
  clearRecycle() {
    return http.delete<{ removed: number }>('/notes/recycle')
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

export const noteFolderApi = {
  list() {
    return http.get<NoteFolder[]>('/note-folders')
  },
  create(payload: NoteFolderPayload) {
    return http.post<NoteFolder>('/note-folders', payload)
  },
  rename(id: string, name: string) {
    return http.put<NoteFolder>(`/note-folders/${id}`, { name })
  },
  move(id: string, parentId: string | null) {
    return http.put<NoteFolder>(`/note-folders/${id}/move`, { parentId })
  },
  /** 删除目录：里面的文档会一起移入回收站 */
  remove(id: string) {
    return http.delete<{ moved: number }>(`/note-folders/${id}`)
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
