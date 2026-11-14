import { defineStore } from 'pinia'
import { computed, watchEffect } from 'vue'
import { useStorage, useWindowSize } from '@vueuse/core'
import { DEFAULT_THEME, THEMES, THEME_MAP, normalizeTheme, type ThemeId } from '@/core/theme/catalog'

const THEME_KEY = 'minidesk:theme-id'
const LEGACY_KEY = 'minidesk:theme'

function initialTheme(): ThemeId {
  if (typeof localStorage === 'undefined') {
    return DEFAULT_THEME
  }
  const saved = localStorage.getItem(THEME_KEY)
  if (saved) {
    return normalizeTheme(saved)
  }
  // 老版本只存了 light/dark/auto，这里迁一次并写回新键
  const legacy = normalizeTheme(localStorage.getItem(LEGACY_KEY))
  localStorage.setItem(THEME_KEY, legacy)
  return legacy
}

/**
 * 标签页图标用同一枚 JI 徽标，颜色取当前主题主色。
 * 浏览器不支持在 SVG 里读 CSS 变量，所以把颜色渲染进 data URI。
 */
function syncFavicon(): void {
  const color =
    getComputedStyle(document.documentElement).getPropertyValue('--brand-500').trim() || '#1772f6'
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">' +
    `<circle cx="32" cy="32" r="32" fill="${color}"/>` +
    '<g fill="none" stroke="#fff" stroke-width="4.2" stroke-linecap="round">' +
    '<path d="M13 20H29"/><path d="M25.5 20v19a5.8 5.8 0 0 1-11.5 0"/>' +
    '<path d="M34.5 20H49"/><path d="M34.5 43H49"/><path d="M41.8 20v23"/></g></svg>'
  const link =
    document.querySelector<HTMLLinkElement>('link[rel="icon"]') ??
    document.head.appendChild(Object.assign(document.createElement('link'), { rel: 'icon' }))
  link.type = 'image/svg+xml'
  link.href = `data:image/svg+xml,${encodeURIComponent(svg)}`
}

/**
 * 全局 UI 状态：主题、侧栏、设备类型。
 * 只放「客户端状态」，服务端数据一律走 vue-query。
 */
export const useAppStore = defineStore('app', () => {
  const theme = useStorage<ThemeId>(THEME_KEY, initialTheme())
  const sidebarCollapsed = useStorage('minidesk:sidebar-collapsed', false)
  /** 侧栏宽度可拖拽，范围 184–320，默认 216 */
  const sidebarWidth = useStorage('minidesk:sidebar-width', 216)
  const { width } = useWindowSize()

  const isMobile = computed(() => width.value < 768)
  const isTablet = computed(() => width.value >= 768 && width.value < 1200)
  const themes = THEMES
  const themeMeta = computed(() => THEME_MAP[theme.value] ?? THEME_MAP[DEFAULT_THEME])
  const isDark = computed(() => themeMeta.value.dark)

  // 主题落 DOM：data-theme 驱动自有 token，.dark 供 Element Plus 暗色变量
  watchEffect(() => {
    const root = document.documentElement
    root.dataset.theme = theme.value
    root.classList.toggle('dark', isDark.value)
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', themeMeta.value.metaColor)
    // data-theme 刚落到 DOM，样式重算后再取主色，否则拿到的是上一套主题的颜色
    requestAnimationFrame(syncFavicon)
  })

  function setTheme(value: ThemeId | string): void {
    theme.value = normalizeTheme(value)
  }

  /** 循环切到下一套主题（快捷键 / 单按钮场景用） */
  function toggleTheme(): void {
    const index = THEMES.findIndex((item) => item.id === theme.value)
    const next = THEMES[(index + 1) % THEMES.length]
    theme.value = next.id
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setSidebarWidth(next: number): void {
    sidebarWidth.value = Math.min(320, Math.max(184, Math.round(next)))
  }

  function resetSidebarWidth(): void {
    sidebarWidth.value = 216
  }

  return {
    theme,
    themes,
    themeMeta,
    sidebarCollapsed,
    sidebarWidth,
    isMobile,
    isTablet,
    isDark,
    toggleTheme,
    setTheme,
    toggleSidebar,
    setSidebarWidth,
    resetSidebarWidth,
  }
})
