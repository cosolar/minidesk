import { http } from '@/core/http/client'

export interface SiteInfo {
  siteName: string
  logoUrl: string | null
  logoWideUrl: string | null
}

export const siteApi = {
  get() {
    return http.get<SiteInfo>('/site')
  },
  rename(siteName: string) {
    return http.put<SiteInfo>('/console/branding/name', { siteName })
  },
  uploadLogo(kind: 'logo' | 'logo-wide', file: File) {
    const form = new FormData()
    form.append('file', file)
    return http.post<SiteInfo>(`/console/branding/${kind}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  removeLogo(kind: 'logo' | 'logo-wide') {
    return http.delete<void>(`/console/branding/${kind}`)
  },
}
