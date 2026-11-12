/**
 * token 与用户态的本地存储。
 * token 放 localStorage（30 天有效、需跨标签页共享）；仅存 token 本身，不存密码。
 */

const TOKEN_KEY = 'minidesk:token'
const ACCOUNTS_KEY = 'minidesk:accounts'

export interface CachedAccount {
  userId: string
  username: string
  nickname: string
  avatarUrl?: string | null
}

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 本机已登录过的账号列表，用于登录页/切换账号快速选择 */
export function getCachedAccounts(): CachedAccount[] {
  try {
    const raw = localStorage.getItem(ACCOUNTS_KEY)
    return raw ? (JSON.parse(raw) as CachedAccount[]) : []
  } catch {
    return []
  }
}

export function cacheAccount(account: CachedAccount): void {
  const list = getCachedAccounts().filter((item) => item.userId !== account.userId)
  list.unshift(account)
  localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(list.slice(0, 5)))
}

export function removeCachedAccount(userId: string): void {
  const list = getCachedAccounts().filter((item) => item.userId !== userId)
  localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(list))
}
