import { http } from '@/core/http/client'

export type AnniversaryPayload = {
  title: string
  type?: string
  person?: string | null
  date: string
  advanceDays?: number
  repeatYearly?: boolean
  lunar?: boolean
  note?: string | null
  cover?: string | null
  important?: boolean
}

export interface AnniversaryVO {
  id: string
  title: string
  type: string
  person: string | null
  date: string
  repeatYearly: boolean
  advanceDays: number
  nextDate: string
  daysLeft: number
  lunar: boolean
  lunarText: string | null
  note: string | null
  cover: string | null
  important: boolean
}

export interface CountdownVO {
  id: string
  title: string
  targetDate: string
  type: string
  color: string | null
  daysLeft: number
}

export const anniversaryApi = {
  list() {
    return http.get<AnniversaryVO[]>('/anniversaries')
  },
  create(payload: AnniversaryPayload) {
    return http.post<AnniversaryVO>('/anniversaries', payload)
  },
  update(id: string, payload: AnniversaryPayload) {
    return http.put<AnniversaryVO>(`/anniversaries/${id}`, payload)
  },
  remove(id: string) {
    return http.delete<void>(`/anniversaries/${id}`)
  },
  listCountdowns() {
    return http.get<CountdownVO[]>('/countdowns')
  },
  createCountdown(payload: { title: string; targetDate: string; type?: string; color?: string }) {
    return http.post<CountdownVO>('/countdowns', payload)
  },
  removeCountdown(id: string) {
    return http.delete<void>(`/countdowns/${id}`)
  },
}
