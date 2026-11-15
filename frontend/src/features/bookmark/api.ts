import { http } from '@/core/http/client'

export interface BookmarkCategory {
  id: number
  name: string
  icon: string
  color: string
  sortOrder: number
  siteCount: number
}

export interface BookmarkSite {
  id: number
  categoryId: number
  name: string
  url: string
  description: string | null
  icon: string | null
  color: string | null
  clickCount: number
  pinned: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export interface BookmarkOverview {
  categories: BookmarkCategory[]
  sites: BookmarkSite[]
  totalClicks: number
  pinnedCount: number
  uncategorizedCount: number
}

export interface CategoryPayload {
  name: string
  icon?: string
  color?: string
}

export interface SitePayload {
  categoryId?: number
  name: string
  url: string
  description?: string
  icon?: string
  color?: string
  pinned?: boolean
}

/** 所有写接口都返回整页快照，前端只维护一份查询缓存 */
export const bookmarkApi = {
  overview: () => http.get<BookmarkOverview>('/bookmarks/overview'),
  createCategory: (payload: CategoryPayload) =>
    http.post<BookmarkOverview>('/bookmarks/categories', payload),
  updateCategory: (id: number, payload: CategoryPayload) =>
    http.put<BookmarkOverview>(`/bookmarks/categories/${id}`, payload),
  deleteCategory: (id: number) =>
    http.delete<BookmarkOverview>(`/bookmarks/categories/${id}`),
  createSite: (payload: SitePayload) =>
    http.post<BookmarkOverview>('/bookmarks/sites', payload),
  updateSite: (id: number, payload: SitePayload) =>
    http.put<BookmarkOverview>(`/bookmarks/sites/${id}`, payload),
  deleteSite: (id: number) =>
    http.delete<BookmarkOverview>(`/bookmarks/sites/${id}`),
  togglePin: (id: number) =>
    http.post<BookmarkOverview>(`/bookmarks/sites/${id}/pin`),
  click: (id: number) => http.post<void>(`/bookmarks/sites/${id}/click`),
  reorder: (type: 'category' | 'site', ids: number[]) =>
    http.post<BookmarkOverview>('/bookmarks/reorder', { type, ids }),
  seed: () => http.post<BookmarkOverview>('/bookmarks/seed'),
}
