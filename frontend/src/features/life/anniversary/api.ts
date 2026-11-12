import { http } from '@/core/http/client'

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
  create(payload: {
    title: string
    type?: string
    person?: string | null
    date: string
    advanceDays?: number
    repeatYearly?: boolean
  }) {
    return http.post<AnniversaryVO>('/anniversaries', payload)
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
