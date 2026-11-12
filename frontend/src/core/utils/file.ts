import { getToken } from './storage'

/**
 * 本地文件地址补 token。
 *
 * `/api/files/{id}/raw` 在 Sa-Token 白名单里（img 标签发不了请求头），
 * 所以改由查询参数携带 token，服务端逐个校验，未登录返回 401。
 */
export function fileUrl(url: string | null | undefined): string {
  if (!url) {
    return ''
  }
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:')) {
    return url
  }
  const token = getToken()
  if (!token) {
    return url
  }
  return `${url}${url.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}`
}
