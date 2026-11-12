import { http } from '@/core/http/client'
import type { TaskVO } from '@/features/work/task/api'

export interface CalendarItem {
  id: string
  source: 'task' | 'event' | 'anniversary' | 'countdown'
  title: string
  start: string
  allDay: boolean
  color: string
  done: boolean
}

export interface StudyWeek {
  totalMinutes: number
  avgMinutesPerDay: number
  growthRate: number | null
  targetCompletion: number
  targetHours: number
  streak: number
  days: Array<{ date: string; weekday: string; minutes: number }>
}

export interface DashboardSummary {
  weather: { temp: number; text: string; city: string; isMock: boolean } | null
  todo: { total: number; done: number; items: TaskVO[] }
  study: StudyWeek | null
  lifeLogs: Array<{ id: string; title: string; date: string; cover: string | null; mood: string | null }>
  calendar: { today: string; events: CalendarItem[] }
  counters: { unreadNotification: number; doingProjects: number; habitStreak: number }
  partial: boolean
}

export const dashboardApi = {
  summary() {
    return http.get<DashboardSummary>('/dashboard/summary')
  },
}
