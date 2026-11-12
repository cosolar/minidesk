import { http } from '@/core/http/client'

export interface SearchHit {
  type: string
  id: string
  title: string
  snippet: string
  meta: string
  route: string
}

export interface SearchGroup {
  key: string
  label: string
  hits: SearchHit[]
}

export interface SearchResult {
  keyword: string
  total: number
  groups: SearchGroup[]
}

export const searchApi = {
  query(keyword: string) {
    return http.get<SearchResult>('/search', { keyword })
  },
}
