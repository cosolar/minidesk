import { getToken } from '@/core/utils/storage'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

export interface UploadedFile {
  id: number
  name: string
  size: number
  mime: string
  url: string
}

/**
 * 文件上传。用 fetch 而非 axios 实例：axios 默认头是 application/json，
 * 传 FormData 时必须让浏览器自己生成 boundary，直接走 fetch 最不容易出错。
 */
export async function uploadFile(file: File, bizType = 'doc'): Promise<UploadedFile> {
  const form = new FormData()
  form.append('file', file)
  form.append('bizType', bizType)

  const response = await fetch(`${baseURL}/files`, {
    method: 'POST',
    headers: { 'MiniDesk-Token': getToken() },
    body: form,
  })
  const body = (await response.json()) as {
    code: number
    message: string
    data: UploadedFile
  }
  if (!response.ok || body.code !== 0) {
    throw new Error(body.message || '上传失败')
  }
  return body.data
}
