import { http } from '@/core/http/client'

export interface Settings {
  city: string
  theme: string
  weekStart: number
  sidebarCollapsed: boolean
}

export interface Integration {
  provider: string
  apiHost: string
  enabled: boolean
  hasKey: boolean
  maskedKey: string
}

export interface TestResult {
  ok: boolean
  message: string
  temp?: number
  text?: string
}

export const settingsApi = {
  get() {
    return http.get<Settings>('/settings')
  },
  update(payload: { city?: string; theme?: string }) {
    return http.put<Settings>('/settings', payload)
  },
  integrations() {
    return http.get<Integration[]>('/settings/integrations')
  },
  saveIntegration(
    provider: string,
    payload: { apiKey?: string; apiHost?: string; enabled?: boolean },
  ) {
    return http.put<Integration>(`/settings/integrations/${provider}`, payload)
  },
  testWeather(city?: string) {
    return http.post<TestResult>(`/settings/weather/test${city ? `?city=${encodeURIComponent(city)}` : ''}`)
  },
}
