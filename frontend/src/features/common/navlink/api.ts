import { http } from '@/core/http/client'

export interface NavLinkVO {
  id: number
  module: 'work' | 'study'
  groupName: string
  title: string
  url: string
  icon: string | null
  description: string | null
  clickCount: number
  sortOrder: number
}

export interface NavLinkPayload {
  module: 'work' | 'study'
  groupName?: string
  title: string
  url: string
  icon?: string | null
  description?: string | null
  sortOrder?: number
}

export const navLinkApi = {
  list(module: 'work' | 'study', keyword?: string) {
    return http.get<NavLinkVO[]>('/nav-links', { module, keyword })
  },
  create(payload: NavLinkPayload) {
    return http.post<NavLinkVO>('/nav-links', payload)
  },
  update(id: number, payload: NavLinkPayload) {
    return http.put<NavLinkVO>(`/nav-links/${id}`, payload)
  },
  click(id: number) {
    return http.post<void>(`/nav-links/${id}/click`)
  },
  remove(id: number) {
    return http.delete<void>(`/nav-links/${id}`)
  },
}
