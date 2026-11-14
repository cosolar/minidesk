<script setup lang="ts">
import { onMounted } from 'vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
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
  <!-- 统一 Element Plus 中文：日期/时间、分页、确认框等全部跟随 -->
  <el-config-provider :locale="zhCn">
    <RouterView />
  </el-config-provider>
</template>
