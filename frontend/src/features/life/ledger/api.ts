import { http } from '@/core/http/client'

export interface LedgerVO {
  id: string
  type: 'expense' | 'income'
  amountCents: number
  categoryId: string | null
  categoryName: string | null
  remark: string | null
  occurDate: string
}

export interface LedgerCategory {
  id: string
  type: 'expense' | 'income'
  name: string
  icon: string | null
  color: string | null
  sortOrder: number
  isSystem: number
}

export interface LedgerSummary {
  incomeCents: number
  expenseCents: number
  balanceCents: number
  budgetCents: number
  budgetUsedRate: number
  categories: Array<{ categoryId: string; name: string; amountCents: number; ratio: number }>
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface LedgerPayload {
  type: 'expense' | 'income'
  amountCents: number
  categoryId?: string | null
  remark?: string | null
  occurDate?: string | null
}

export const ledgerApi = {
  page(params: { month?: string; type?: string; page?: number; size?: number }) {
    return http.get<PageResult<LedgerVO>>('/ledger', params)
  },
  summary(month?: string) {
    return http.get<LedgerSummary>('/ledger/summary', { month })
  },
  categories() {
    return http.get<LedgerCategory[]>('/ledger/categories')
  },
  create(payload: LedgerPayload) {
    return http.post<LedgerVO>('/ledger', payload)
  },
  remove(id: string) {
    return http.delete<void>(`/ledger/${id}`)
  },
}
