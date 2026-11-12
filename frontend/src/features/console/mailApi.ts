import { http } from '@/core/http/client'

export interface MailConfig {
  host: string
  port: number
  username: string
  from: string
  ssl: boolean
  maskedPassword: string
  configured: boolean
  enabled: boolean
}

export const mailApi = {
  get() {
    return http.get<MailConfig>('/console/mail')
  },
  save(payload: MailConfig) {
    return http.put<MailConfig>('/console/mail', payload)
  },
  test(to: string) {
    return http.post<void>('/console/mail/test', null, { params: { to } })
  },
}
