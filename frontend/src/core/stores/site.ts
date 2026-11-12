import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import defaultMark from '@/assets/logo-mark.webp'
import defaultWide from '@/assets/logo-wide.webp'
import { siteApi } from '@/core/api/site'

/**
 * 站点品牌：后台可替换站点名与两张 logo。
 * 未配置时回退到内置资源，保证首次启动也有完整的品牌观感。
 */
export const useSiteStore = defineStore('site', () => {
  const siteName = ref('MiniDesk')
  const logoUrl = ref<string | null>(null)
  const logoWideUrl = ref<string | null>(null)
  const loaded = ref(false)

  const wideLogo = computed(() => logoWideUrl.value || defaultWide)
  const markLogo = computed(() => logoUrl.value || defaultMark)

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

  return { siteName, logoUrl, logoWideUrl, loaded, wideLogo, markLogo, load, apply }
})
