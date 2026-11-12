import { http } from '@/core/http/client'

export interface BookVO {
  id: string
  title: string
  author: string | null
  category: string | null
  totalPages: number
  readPages: number
  progress: number
  status: string
  rating: number | null
  startDate: string | null
  finishDate: string | null
}

export interface BookNote {
  id: string
  content: string
  pageNo: number | null
  createdAt: string
}

export const bookApi = {
  list(status?: string) {
    return http.get<BookVO[]>('/books', { status })
  },
  create(payload: { title: string; author?: string; category?: string; totalPages?: number }) {
    return http.post<BookVO>('/books', payload)
  },
  update(id: string, payload: { readPages?: number; status?: string; rating?: number }) {
    return http.put<BookVO>(`/books/${id}`, payload)
  },
  remove(id: string) {
    return http.delete<void>(`/books/${id}`)
  },
  notes(id: string) {
    return http.get<BookNote[]>(`/books/${id}/notes`)
  },
  addNote(id: string, payload: { content: string; pageNo?: number }) {
    return http.post<void>(`/books/${id}/notes`, payload)
  },
}
