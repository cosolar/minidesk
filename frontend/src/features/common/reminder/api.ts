import { http } from '@/core/http/client'

export type ReminderKind = 'task' | 'birthday' | 'anniversary' | 'countdown' | 'plan'

export interface ReminderVO {
  id: string
  kind: ReminderKind
  title: string
  subtitle: string
  date: string
  days: number
  level: 'overdue' | 'today' | 'urgent' | 'soon'
  route: string
  done: boolean
}

export const reminderApi = {
  list() {
    return http.get<ReminderVO[]>('/reminders')
  },
}
