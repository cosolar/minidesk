import { http } from '@/core/http/client'
import { getToken } from '@/core/utils/storage'

export interface DriveFile {
  id: number
  parentId: number
  name: string
  type: 'file' | 'folder'
  size: number
  mime: string | null
  storage: string
  childCount: number
  link: string
  createdAt: string
  updatedAt: string
}

export interface DriveStats {
  fileCount: number
  folderCount: number
  usedBytes: number
  capacityBytes: number
  imageCount: number
  documentCount: number
  otherCount: number
  storageType: string
  storageLabel: string
  ready: boolean
}

export interface DriveConfig {
  storageType: string
  endpoint: string
  bucket: string
  region: string
  accessKey: string
  maskedSecret: string
  hasSecret: boolean
  pathStyle: boolean
  prefix: string
  publicUrl: string
  acl: string
  ready: boolean
}

export interface DriveConfigPayload {
  storageType: string
  endpoint: string
  bucket: string
  region: string
  accessKey: string
  secretKey: string
  pathStyle: boolean
  prefix: string
  publicUrl: string
  acl: string
}

export interface DriveTestResult {
  ok: boolean
  message: string
  storageType: string
}

export interface UploadTask {
  name: string
  size: number
  percent: number
  status: 'uploading' | 'done' | 'error'
  message?: string
}

export const driveApi = {
  list: (parentId: number, keyword?: string) =>
    http.get<DriveFile[]>('/drive/files', { parentId, keyword: keyword || undefined }),
  stats: () => http.get<DriveStats>('/drive/stats'),
  createFolder: (parentId: number, name: string) =>
    http.post<DriveFile>('/drive/folders', { parentId, name }),
  rename: (id: number, name: string) =>
    http.put<DriveFile>(`/drive/files/${id}`, { name }),
  remove: (id: number) => http.delete<void>(`/drive/files/${id}`),
  config: () => http.get<DriveConfig>('/drive/config'),
  saveConfig: (payload: DriveConfigPayload) =>
    http.put<DriveConfig>('/drive/config', payload),
  testConfig: () => http.post<DriveTestResult>('/drive/config/test'),
}

/** 图片预览 / 下载都要带 token；S3 直链不需要 */
export function fileUrl(file: DriveFile, download = false): string {
  if (/^https?:\/\//.test(file.link)) {
    return file.link
  }
  const token = encodeURIComponent(getToken())
  return `${file.link}?token=${token}${download ? '&download=true' : ''}`
}

/** 用 XHR 上传，才能拿到真实的进度百分比 */
export function uploadDriveFile(
  file: File,
  parentId: number,
  onProgress: (percent: number) => void,
): Promise<DriveFile> {
  const baseURL = import.meta.env.VITE_API_BASE || '/api'
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', `${baseURL}/drive/files?parentId=${parentId}`)
    xhr.setRequestHeader('MiniDesk-Token', getToken())
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    }
    xhr.onload = () => {
      try {
        const body = JSON.parse(xhr.responseText) as { code: number; message: string; data: DriveFile }
        if (xhr.status >= 200 && xhr.status < 300 && body.code === 0) {
          resolve(body.data)
        } else {
          reject(new Error(body.message || '上传失败'))
        }
      } catch {
        reject(new Error('上传失败，请检查网络或存储配置'))
      }
    }
    xhr.onerror = () => reject(new Error('网络异常，上传失败'))
    const form = new FormData()
    form.append('file', file)
    xhr.send(form)
  })
}
