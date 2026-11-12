<script setup lang="ts">
import { ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
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
  </div>
</template>

<style scoped lang="scss">
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
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
    padding: 4px 14px 24px;
  }
}
</style>
