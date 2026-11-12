/**
 * 接口通用类型。
 * 后端统一响应体见 docs/接口清单.md §1.2。
 */

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
}

/** 业务错误：code 非 0 时抛出，页面可据此做分支处理 */
export class BizError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BizError'
    this.code = code
  }
}
