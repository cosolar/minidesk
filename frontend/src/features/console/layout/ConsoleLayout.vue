<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, FolderOpened, Setting, Timer, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/core/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/console', title: '概览', icon: DataBoard },
  { path: '/console/users', title: '用户管理', icon: User },
  { path: '/console/jobs', title: '定时任务', icon: Timer },
  { path: '/console/backups', title: '备份恢复', icon: FolderOpened },
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
// 后台沿用用户端的设计 token：五套主题与深色自动生效，不再写死深蓝 + 蓝主色
.console {
  display: flex;
  gap: 12px;
  height: 100vh;
  height: 100dvh;
  padding: 12px;
  overflow: hidden;
  background:
    radial-gradient(
      72% 52% at 0% 0%,
      color-mix(in srgb, var(--brand-200) 32%, transparent),
      transparent 62%
    ),
    radial-gradient(
      58% 46% at 100% 0%,
      color-mix(in srgb, var(--brand-100) 28%, transparent),
      transparent 58%
    ),
    var(--bg-page);
  color: var(--text-primary);
}

.console__sidebar {
  display: flex;
  flex-direction: column;
  width: 212px;
  flex: 0 0 212px;
  padding: 16px 12px;
  @include card($radius-xl);
  overflow: hidden;
}

.console__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px 18px;

  strong {
    display: block;
    color: var(--text-primary);
    font-size: 14px;
  }

  small {
    display: block;
    margin-top: 1px;
    color: var(--text-tertiary);
    font-size: 10px;
  }
}

.console__logo {
  @include flex-center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(145deg, var(--brand-400), var(--brand-600));
  color: var(--text-inverse);
  font-weight: 700;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--brand-500) 28%, transparent);
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
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 13px;
  transition:
    background-color $duration-fast $ease-out,
    color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-primary);
  }

  &.is-active {
    background: linear-gradient(135deg, var(--brand-400), var(--brand-600));
    color: var(--text-inverse);
    font-weight: 500;
    box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 30%, transparent);
  }
}

.console__foot {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.console__back {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    background-color $duration-fast $ease-out,
    color $duration-fast $ease-out;

  &:hover {
    background: var(--bg-hover);
    color: var(--brand-600);
  }
}

.console__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.console__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  flex: 0 0 auto;
  padding: 0 8px 0 4px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 68%, transparent);
}

.console__crumb {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.console__user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 12.5px;
}

.console__badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--brand-100) 76%, transparent);
  color: var(--brand-700);
  font-size: 10px;
  font-weight: 600;
}

.console__content {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 2px 16px 0;

  > :deep(*) {
    flex: 1 0 auto;
    min-width: 0;
  }
}

@media (max-width: 767px) {
  .console {
    flex-direction: column;
    gap: 8px;
    padding: 8px;
  }

  .console__sidebar {
    width: 100%;
    flex: 0 0 auto;
    padding: 10px 12px;
  }

  .console__brand {
    padding: 0 4px 10px;
  }

  .console__nav {
    flex-direction: row;
    gap: 4px;
    overflow-x: auto;
    /* 窄屏横向滚动条会压住导航文字，视觉上只保留滚动能力 */
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .console__nav-item {
    flex: 0 0 auto;
    height: 34px;
    white-space: nowrap;
  }

  .console__foot {
    /* 窄屏也要留回工作台的出口：只在尾部收窄，不要整块隐藏 */
    display: flex;
    justify-content: flex-end;
    margin-top: 6px;
    padding-top: 6px;
  }

  .console__back {
    width: auto;
    height: 28px;
    padding: 0 6px;
    font-size: 11.5px;
  }

  .console__header {
    height: 44px;
    padding: 0 4px;
  }

  .console__content {
    padding: 10px 0 12px;
  }
}
</style>
