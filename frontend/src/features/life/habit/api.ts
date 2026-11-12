import { http } from '@/core/http/client'

export interface HabitVO {
  id: string
  module: 'study' | 'life'
  name: string
  icon: string | null
  color: string | null
  type: 'boolean' | 'count'
  targetValue: number
  unit: string | null
  todayValue: number
  checked: boolean
  streak: number
}

export interface HabitHeatmap {
  habitId: string
  name: string
  year: number
  currentStreak: number
  longestStreak: number
  totalDays: number
  days: Array<{ date: string; value: number }>
}

export interface HabitPayload {
  module?: string
  name: string
  icon?: string | null
  color?: string | null
  type?: string
  targetValue?: number
  unit?: string | null
}

export const habitApi = {
  list(module?: string) {
    return http.get<HabitVO[]>('/habits', { module })
  },
  create(payload: HabitPayload) {
    return http.post<HabitVO>('/habits', payload)
  },
  checkin(id: string, value: number, date?: string) {
    return http.post<void>(`/habits/${id}/checkin`, { value, date })
  },
  heatmap(id: string, year?: number) {
    return http.get<HabitHeatmap>(`/habits/${id}/heatmap`, { year })
  },
  remove(id: string) {
    return http.delete<void>(`/habits/${id}`)
  },
}
