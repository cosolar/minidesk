import { http } from '@/core/http/client'

export type KbScope = 'mine' | 'public'

export interface KbStatus {
  configured: boolean
  enabled: boolean
  connected: boolean
  apiHost: string
  siteUrl: string
  username: string
  displayName: string
  defaultOrg: string
  tenants: KbTenant[]
  message: string
}

export interface KbTenant {
  slug: string
  name: string
  role: string
}

export interface KbBook {
  id: string
  name: string
  slug: string
  orgSlug: string
  description: string
  visibility: string
  docCount: number
  tags: string[]
  updatedText: string
  coverSrc: string
  scope: KbScope
  url: string
}

export interface KbBookPage {
  list: KbBook[]
  total: number
  page: number
  size: number
}

export interface KbDocNode {
  name: string
  path: string
  encodedPath: string
  type: string
  size: number
  modifiedAt: string
  children: KbDocNode[]
}

export interface KbDocContent {
  scope: KbScope
  path: string
  name: string
  title: string
  markdown: string
  html: string
  modifiedAt: string
  size: number
  sourceUrl: string
}

export interface KbSearchHit {
  title: string
  path: string
  bookName: string
  orgSlug: string
  slug: string
  scope: KbScope
}

export interface KbCreateResult {
  path: string
  name: string
  url: string
}

export interface BookQuery {
  scope: KbScope
  org?: string
  keyword?: string
  page?: number
  size?: number
}

export interface DocQuery {
  scope: KbScope
  org?: string
  slug: string
  path: string
}

export const kbApi = {
  status: () => http.get<KbStatus>('/kb/status'),
  test: () => http.post<KbStatus>('/kb/test'),
  books: (params: BookQuery) => http.get<KbBookPage>('/kb/books', params),
  tree: (params: { scope: KbScope; org?: string; slug: string }) =>
    http.get<KbDocNode[]>('/kb/tree', params),
  doc: (params: DocQuery) => http.get<KbDocContent>('/kb/doc', params),
  search: (params: { scope: KbScope; org?: string; slug: string; q: string; bookName?: string }) =>
    http.get<KbSearchHit[]>('/kb/search', params),
  createBook: (payload: { org?: string; name: string; description?: string; visibility?: string }) =>
    http.post<KbBook>('/kb/books', payload),
  createDoc: (payload: { org?: string; slug: string; path: string; content: string }) =>
    http.post<KbCreateResult>('/kb/docs', payload),
}
