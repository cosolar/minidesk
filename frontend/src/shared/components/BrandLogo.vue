/**
 * 站点品牌标识：默认使用设计原稿的 JI 徽标与「极简工作台」横版字标，
 * 后台一旦上传自定义 logo 就优先用上传图。
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useSiteStore } from '@/core/stores/site'
import defaultMark from '@/assets/logo-mark.webp'
import defaultWide from '@/assets/logo-wide.webp'

interface Props {
  mode?: 'wide' | 'mark'
  /** 大尺寸，登录页用 lg */
  size?: 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'wide',
  size: 'md',
})

const siteStore = useSiteStore()

const src = computed(() => {
  if (props.mode === 'mark') {
    return siteStore.logoUrl || defaultMark
  }
  return siteStore.logoWideUrl || defaultWide
})
</script>

<template>
  <img
    :src="src"
    :alt="siteStore.siteName"
    class="brand"
    :class="[`brand--${mode}`, { 'brand--lg': size === 'lg' }]"
  />
</template>

<style scoped lang="scss">
/* 两张图都带透明通道，直接叠在浅色背景上即可，不需要 multiply */
.brand {
  display: block;
  max-width: 100%;
  object-fit: contain;
  /* 内置图与后台上传的图都跟着主题换色：--logo-filter 由各主题在 theme.scss 里定义 */
  filter: var(--logo-filter, none);
  transition: filter 0.3s ease;

  &--wide {
    height: 36px;
    width: auto;
  }

  &--mark {
    width: 36px;
    height: 36px;
  }

  &--lg.brand--wide {
    height: 46px;
  }

  &--lg.brand--mark {
    width: 46px;
    height: 46px;
  }
}
</style>
