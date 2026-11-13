<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Calendar, HomeFilled, List, Notebook, User } from '@element-plus/icons-vue'
import type { Component } from 'vue'

interface TabItem {
  path: string
  label: string
  icon: Component
  match: (path: string) => boolean
}

/** 窄屏只保留 5 个高频入口；其余功能走顶栏菜单抽屉。 */
const tabs: TabItem[] = [
  { path: '/', label: '工作台', icon: HomeFilled, match: (path) => path === '/' },
  { path: '/todo', label: '待办', icon: List, match: (path) => path.startsWith('/todo') },
  { path: '/calendar', label: '日历', icon: Calendar, match: (path) => path.startsWith('/calendar') },
  { path: '/note', label: '笔记', icon: Notebook, match: (path) => path.startsWith('/note') },
  { path: '/profile', label: '我的', icon: User, match: (path) => path.startsWith('/profile') },
]

const route = useRoute()
</script>

<template>
  <nav class="mobile-tabs" aria-label="主导航">
    <RouterLink
      v-for="item in tabs"
      :key="item.path"
      :to="item.path"
      class="mobile-tabs__item"
      :class="{ 'is-active': item.match(route.path) }"
      :aria-current="item.match(route.path) ? 'page' : undefined"
    >
      <el-icon class="mobile-tabs__icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss">
.mobile-tabs {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  height: calc(58px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
  box-shadow: 0 -6px 22px rgba(23, 74, 60, 0.07);
}

.mobile-tabs__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-tertiary);
  font-size: 10.5px;
  text-decoration: none;
  transition: color $duration-fast $ease-out;

  &:active {
    transform: scale(0.96);
  }

  &.is-active {
    color: var(--brand-600);
    font-weight: 600;
  }
}

.mobile-tabs__icon {
  font-size: 19px;
  line-height: 1;
}
</style>
