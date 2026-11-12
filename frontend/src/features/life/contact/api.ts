import { http } from '@/core/http/client'

export interface ContactVO {
  id: number
  name: string
  type: 'client' | 'colleague' | 'partner' | 'family'
  company: string | null
  position: string | null
  phone: string | null
  email: string | null
  wechat: string | null
  birthday: string | null
  daysToBirthday: number | null
  note: string | null
  initial: string
}

export interface ContactPayload {
  name: string
  type?: string
  company?: string | null
  position?: string | null
  phone?: string | null
  email?: string | null
  wechat?: string | null
  birthday?: string | null
  note?: string | null
}

export const contactApi = {
  list(params: { keyword?: string; type?: string }) {
    return http.get<ContactVO[]>('/contacts', params)
  },
  create(payload: ContactPayload) {
    return http.post<ContactVO>('/contacts', payload)
  },
  update(id: number, payload: ContactPayload) {
    return http.put<ContactVO>(`/contacts/${id}`, payload)
  },
  remove(id: number) {
    return http.delete<void>(`/contacts/${id}`)
  },
}
