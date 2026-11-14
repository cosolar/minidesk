import { defineStore } from 'pinia'
import { ref } from 'vue'
import { siteApi } from '@/core/api/site'

/**
 * 站点品牌：后台可替换站点名与两张 logo。
 * 未配置时由 BrandLogo 组件用内联 SVG 兜底，颜色跟随当前主题。
 */
export const useSiteStore = defineStore('site', () => {
  const siteName = ref('MiniDesk')
  const logoUrl = ref<string | null>(null)
  const logoWideUrl = ref<string | null>(null)
  const loaded = ref(false)

  async function load(): Promise<void> {
    try {
      const info = await siteApi.get()
      if (info.siteName) {
        siteName.value = info.siteName
      }
      logoUrl.value = info.logoUrl
      logoWideUrl.value = info.logoWideUrl
    } catch {
      // 站点信息拿不到就用内置品牌，不阻塞页面
    } finally {
      loaded.value = true
    }
  }

  function apply(info: { siteName: string; logoUrl: string | null; logoWideUrl: string | null }): void {
    siteName.value = info.siteName || siteName.value
    logoUrl.value = info.logoUrl
    logoWideUrl.value = info.logoWideUrl
  }

  return { siteName, logoUrl, logoWideUrl, loaded, load, apply }
})
