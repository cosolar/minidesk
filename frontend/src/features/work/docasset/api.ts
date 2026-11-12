import { http } from '@/core/http/client'

export type DocType = 'markdown' | 'link' | 'file'

export interface DocAssetVO {
  id: number
  title: string
  docType: DocType
  provider: string | null
  url: string | null
  content: string | null
  fileId: number | null
  category: string | null
  tags: string | null
  description: string | null
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface DocAssetPayload {
  title: string
  docType: DocType
  provider?: string | null
  url?: string | null
  content?: string | null
  fileId?: number | null
  category?: string | null
  tags?: string | null
  description?: string | null
  pinned?: boolean
}

export const docAssetApi = {
  list(params: { docType?: string; category?: string; keyword?: string }) {
    return http.get<DocAssetVO[]>('/doc-assets', params)
  },
  detail(id: number) {
    return http.get<DocAssetVO>(`/doc-assets/${id}`)
  },
  create(payload: DocAssetPayload) {
    return http.post<DocAssetVO>('/doc-assets', payload)
  },
  update(id: number, payload: DocAssetPayload) {
    return http.put<DocAssetVO>(`/doc-assets/${id}`, payload)
  },
  remove(id: number) {
    return http.delete<void>(`/doc-assets/${id}`)
  },
}
