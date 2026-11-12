import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { Router } from 'vue-router'
import { useUserStore } from '@/core/stores/user'
import { authApi } from '@/features/auth/api'

NProgress.configure({ showSpinner: false, minimum: 0.12, speed: 320 })

/** 并发导航只恢复一次会话 */
let hydrating: Promise<boolean> | null = null

async function restoreSession(): Promise<boolean> {
  const user = useUserStore()
  if (!user.token || user.profile) {
    return true
  }
  hydrating ??= authApi
    .session()
    .then((info) => {
      user.applySessionInfo(info.user, info.roles, info.permissions)
      return true
    })
    .catch(() => {
      user.clearSession()
      return false
    })
    .finally(() => {
      hydrating = null
    })
  return hydrating
}

export function setupRouterGuard(router: Router): void {
  router.beforeEach(async (to) => {
    NProgress.start()

    const user = useUserStore()

    if (to.meta.public) {
      return true
    }

    if (!user.isLoggedIn) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    // token 存在但内存态丢失（刷新页面）→ 拉一次 session
    const ok = await restoreSession()
    if (!ok) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    if (to.meta.requiresAdmin && !user.isAdmin) {
      return { name: 'forbidden' }
    }

    return true
  })

  router.afterEach((to) => {
    NProgress.done()
    document.title = to.meta.title ? `${to.meta.title} · MiniDesk` : '极简工作台 · MiniDesk'
  })

  router.onError(() => {
    NProgress.done()
  })
}
