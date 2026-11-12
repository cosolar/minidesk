<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, Setting, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/core/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/console', title: '概览', icon: DataBoard },
  { path: '/console/users', title: '用户管理', icon: User },
  { path: '/console/settings', title: '系统设置', icon: Setting },
]

const activePath = computed(() => route.path)

async function backToDesk(): Promise<void> {
  await router.push('/')
}
</script>

<template>
  <div class="console">
    <aside class="console__sidebar">
      <div class="console__brand">
        <span class="console__logo">M</span>
        <div>
          <strong>MiniDesk</strong>
          <small>管理控制台</small>
        </div>
      </div>

      <nav class="console__nav">
        <RouterLink
          v-for="menu in menus"
          :key="menu.path"
          :to="menu.path"
          class="console__nav-item"
          :class="{ 'is-active': activePath === menu.path }"
        >
          <el-icon><component :is="menu.icon" /></el-icon>
          {{ menu.title }}
        </RouterLink>
      </nav>

      <div class="console__foot">
        <button type="button" class="console__back" @click="backToDesk">← 返回工作台</button>
      </div>
    </aside>

    <div class="console__main">
      <header class="console__header">
        <span class="console__crumb">管理控制台</span>
        <div class="console__user">
          <span>{{ userStore.displayName }}</span>
          <span class="console__badge">ADMIN</span>
        </div>
      </header>

      <main class="console__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.console {
  --console-bg: #f5f7fa;
  --console-side: #1b2230;
  --console-side-hover: #242d3d;
  --console-primary: #4c6fff;

  display: flex;
  min-height: 100vh;
  background: var(--console-bg);
}

.console__sidebar {
  display: flex;
  flex-direction: column;
  width: 208px;
  flex: 0 0 208px;
  padding: 18px 12px;
  background: var(--console-side);
  color: #e6eaf2;
}

.console__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 20px;

  strong {
    display: block;
    font-size: 14px;
  }

  small {
    display: block;
    margin-top: 1px;
    font-size: 10px;
    color: #7d8aa3;
  }
}

.console__logo {
  @include flex-center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--console-primary);
  color: #fff;
  font-weight: 700;
}

.console__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.console__nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 10px;
  border-radius: 8px;
  color: #a9b4c8;
  font-size: 13px;
  transition: all 0.16s ease;

  &:hover {
    background: var(--console-side-hover);
    color: #fff;
  }

  &.is-active {
    background: var(--console-primary);
    color: #fff;
    font-weight: 500;
  }
}

.console__foot {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.console__back {
  width: 100%;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #7d8aa3;
  font-size: 12px;
  text-align: left;
  padding: 0 10px;
  cursor: pointer;

  &:hover {
    background: var(--console-side-hover);
    color: #fff;
  }
}

.console__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.console__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
}

.console__crumb {
  font-size: 13px;
  color: #6b7c87;
}

.console__user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}

.console__badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: #eef2ff;
  color: var(--console-primary);
  font-size: 10px;
  font-weight: 600;
}

.console__content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>
