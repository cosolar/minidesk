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
  /** 实际查询的城市，用来确认设置是否生效 */
  city?: string
  temp?: number
  text?: string
  /** 实际生效的数据源：cma / open-meteo / mock */
  source: string
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
