import { http } from '@/core/http/client'

export interface StatsPoint {
  label: string
  primary: number
  secondary: number
}

export interface StatsVO {
  work: {
    weekCreated: number
    weekDone: number
    overdue: number
    activeProjects: number
    completionRate: number
    trend: StatsPoint[]
  }
  study: {
    monthMinutes: number
    streakDays: number
    planProgress: number
    planCount: number
    trend: StatsPoint[]
  }
  life: {
    monthIncome: number
    monthExpense: number
    balance: number
    budgetCents: number
    budgetUsedRate: number
    trend: StatsPoint[]
    topCategories: Array<{ name: string; cents: number; ratio: number }>
  }
}

export const statsApi = {
  summary() {
    return http.get<StatsVO>('/stats/summary')
  },
}
