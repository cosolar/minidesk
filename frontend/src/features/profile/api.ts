import { getToken } from '@/core/utils/storage'
import { http } from '@/core/http/client'
import type { UserProfile } from '@/core/stores/user'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

export interface ProfilePayload {
  nickname?: string
  email?: string | null
}

export interface PasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export const profileApi = {
  get() {
    return http.get<UserProfile>('/user/profile')
  },
  update(payload: ProfilePayload) {
    return http.put<UserProfile>('/user/profile', payload)
  },
  changePassword(payload: PasswordPayload) {
    return http.put<void>('/user/password', payload)
  },
  /** 头像走 fetch：FormData 需要浏览器自己生成 boundary */
  async uploadAvatar(file: File): Promise<UserProfile> {
    const form = new FormData()
    form.append('file', file)
    const response = await fetch(`${baseURL}/user/avatar`, {
      method: 'POST',
      headers: { 'MiniDesk-Token': getToken() },
      body: form,
    })
    const body = (await response.json()) as {
      code: number
      message: string
      data: UserProfile
    }
    if (!response.ok || body.code !== 0) {
      throw new Error(body.message || '头像上传失败')
    }
    return body.data
  },
}
