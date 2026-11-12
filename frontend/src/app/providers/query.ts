import { QueryClient } from '@tanstack/vue-query'

/**
 * 服务端状态统一由 vue-query 管理。
 * 具体各资源的 staleTime 在各自 feature 的 useQuery 中覆盖。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: 0,
    },
  },
})
