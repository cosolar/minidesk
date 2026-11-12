import { http } from '@/core/http/client'
import type { CalendarItem } from '@/features/dashboard/api'

export interface CalendarEventPayload {
  title: string
  description?: string | null
  startTime: string
  endTime?: string | null
  allDay?: boolean
  location?: string | null
  color?: string | null
}

export const calendarApi = {
  events(start: string, end: string) {
    return http.get<CalendarItem[]>('/calendar/events', { start, end })
  },
  createEvent(payload: CalendarEventPayload) {
    return http.post<number>('/calendar/events', payload)
  },
  deleteEvent(id: string) {
    return http.delete<void>(`/calendar/events/${id}`)
  },
}
