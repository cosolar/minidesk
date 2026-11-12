import { http } from '@/core/http/client'

export interface ShoppingItem {
  id: string
  name: string
  quantity: number
  unit: string | null
  priceCents: number
  category: string | null
  bought: boolean
}

export interface ShoppingList {
  id: string
  name: string
  icon: string | null
  budgetCents: number
  status: string
  itemCount: number
  boughtCount: number
  totalCents: number
  items: ShoppingItem[]
}

export const shoppingApi = {
  lists() {
    return http.get<ShoppingList[]>('/shopping/lists')
  },
  createList(payload: { name: string; icon?: string; budgetCents?: number }) {
    return http.post<ShoppingList>('/shopping/lists', payload)
  },
  removeList(id: string) {
    return http.delete<void>(`/shopping/lists/${id}`)
  },
  addItem(
    listId: string,
    payload: { name: string; quantity?: number; unit?: string; priceCents?: number; category?: string },
  ) {
    return http.post<void>(`/shopping/lists/${listId}/items`, payload)
  },
  toggleItem(id: string, bought: boolean) {
    return http.patch<void>(`/shopping/items/${id}/bought`, { bought })
  },
  removeItem(id: string) {
    return http.delete<void>(`/shopping/items/${id}`)
  },
}
