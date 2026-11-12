<script setup lang="ts">
import { onMounted } from 'vue'
import { useAppStore } from '@/core/stores/app'
import { useSiteStore } from '@/core/stores/site'

// 初始化即应用主题（内部 watchEffect 会同步 data-theme 与 .dark）
useAppStore()
const siteStore = useSiteStore()
onMounted(() => {
  siteStore.load()
})
</script>

<template>
  <RouterView />
</template>
