import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { BizError, type ApiResult } from './types'
import { clearToken, getToken } from '@/core/utils/storage'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

/** 401 只提示一次，避免并发请求刷屏 */
let redirecting = false

const service: AxiosInstance = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.set('MiniDesk-Token', token)
  }
  config.headers.set('X-Request-Id', createRequestId())
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown> | undefined
    // 二进制流（导出/下载）不走统一包装
    if (!body || typeof body.code !== 'number') {
      return response.data
    }
    if (body.code === 0) {
      return body.data
    }
    return handleBusinessError(body.code, body.message)
  },
  (error: AxiosError<ApiResult<unknown>>) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = body?.message

    if (status === 401 || body?.code === 40101 || body?.code === 40102) {
      return handleUnauthorized()
    }
    if (status === 403 || body?.code === 40301) {
      ElMessage.error(message || '没有操作权限')
      return Promise.reject(new BizError(40301, message || '没有操作权限'))
    }
    if (body?.code === 40302) {
      ElMessage.error(message || '账号已被禁用')
      return Promise.reject(new BizError(40302, message || '账号已被禁用'))
    }
    if (status === 429 || body?.code === 42901) {
      ElMessage.warning(message || '操作太频繁，请稍后再试')
      return Promise.reject(new BizError(42901, message || '操作太频繁'))
    }
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
      return Promise.reject(error)
    }
    if (!error.response) {
      ElMessage.error('网络异常，请检查连接')
      return Promise.reject(error)
    }
    ElMessage.error(message || '服务异常，请稍后重试')
    return Promise.reject(error)
  },
)

function handleBusinessError(code: number, message: string): never {
  if (code === 40101 || code === 40102) {
    handleUnauthorized()
    throw new BizError(code, message)
  }
  ElMessage.error(message || '操作失败')
  throw new BizError(code, message)
}

function handleUnauthorized(): never {
  clearToken()
  if (!redirecting && !location.pathname.startsWith('/login')) {
    redirecting = true
    const redirect = encodeURIComponent(location.pathname + location.search)
    location.replace(`/login?redirect=${redirect}`)
  }
  throw new BizError(40101, '登录已过期，请重新登录')
}

function createRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 统一请求入口：泛型即返回的业务数据类型（已剥离 Result 外壳） */
export function request<T>(config: AxiosRequestConfig): Promise<T> {
  // 响应拦截器已把 Result 拆成 data，axios 的类型无法表达这一层，
  // 故在此处做一次收口转换，调用方拿到的就是业务类型。
  return service.request(config) as unknown as Promise<T>
}

export const http = {
  get: <T>(url: string, params?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, url, method: 'GET', params }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, url, method: 'POST', data }),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, url, method: 'PUT', data }),
  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, url, method: 'PATCH', data }),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    request<T>({ ...config, url, method: 'DELETE' }),
}

export default service
