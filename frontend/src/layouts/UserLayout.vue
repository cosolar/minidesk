<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import MobileTabBar from './components/MobileTabBar.vue'
import { useAppStore } from '@/core/stores/app'

const appStore = useAppStore()
const drawerVisible = ref(false)

function onToggleSidebar(): void {
  if (appStore.isMobile) {
    drawerVisible.value = true
    return
  }
  appStore.toggleSidebar()
}
</script>

<template>
  <div class="layout">
    <AppSidebar v-if="!appStore.isMobile" />

    <el-drawer
      v-if="appStore.isMobile"
      v-model="drawerVisible"
      direction="ltr"
      size="220px"
      :with-header="false"
      class="sidebar-drawer"
    >
      <AppSidebar @navigate="drawerVisible = false" />
    </el-drawer>

    <div class="layout__main">
      <AppHeader @toggle-sidebar="onToggleSidebar" />
      <main class="layout__content">
        <RouterView v-slot="{ Component }">
          <Transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>

    <MobileTabBar v-if="appStore.isMobile" />
  </div>
</template>

<style scoped lang="scss">
.layout {
  position: relative;
  isolation: isolate;
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);

  // 品牌色晕染：纯色底上做玻璃卡片会像「白盒子」，加一层很淡的色雾，
  // 卡片后面的模糊才有内容可糊，层次和冷暖也更接近设计稿
  &::before {
    content: '';
    position: fixed;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    background:
      radial-gradient(
        780px 460px at 6% -12%,
        color-mix(in srgb, var(--brand-400) 26%, transparent),
        transparent 70%
      ),
      radial-gradient(
        680px 420px at 96% -6%,
        color-mix(in srgb, var(--brand-500) 18%, transparent),
        transparent 72%
      ),
      radial-gradient(
        720px 520px at 82% 108%,
        color-mix(in srgb, var(--brand-400) 16%, transparent),
        transparent 74%
      );
  }
}

.layout__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.layout__content {
  flex: 1;
  width: 100%;
  max-width: 1520px;
  margin: 0 auto;
  padding: 4px $content-padding 32px;

  @include respond-below(sm) {
    padding: 4px 14px 84px;
  }
}
</style>
