<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { homeNav, navGroups, systemNav, unifiedIcons, unifiedNav } from '@/app/router/nav'
import { useAppStore } from '@/core/stores/app'
import { useSiteStore } from '@/core/stores/site'
import BrandLogo from '@/shared/components/BrandLogo.vue'
import lakeImage from '@/assets/hero-lake.jpg'

const emit = defineEmits<{ navigate: [] }>()

const route = useRoute()
const appStore = useAppStore()
const siteStore = useSiteStore()

const expanded = ref<string[]>([])
const resizing = ref(false)

const activeGroup = computed(
  () => navGroups.find((group) => route.path.startsWith(group.basePath))?.key ?? '',
)

watch(
  activeGroup,
  (key) => {
    if (key && !expanded.value.includes(key)) {
      expanded.value = [...expanded.value, key]
    }
  },
  { immediate: true },
)

const sidebarStyle = computed(() => {
  const width = appStore.sidebarCollapsed ? 72 : appStore.sidebarWidth
  return { width: `${width}px`, flexBasis: `${width}px` }
})

function toggleGroup(key: string): void {
  expanded.value = expanded.value.includes(key)
    ? expanded.value.filter((item) => item !== key)
    : [...expanded.value, key]
}

const isHome = computed(() => route.path === homeNav.path)

function isActive(path: string): boolean {
  return route.path === path
}

function onNavigate(): void {
  emit('navigate')
}

/** 拖拽调整宽度：拖动期间关闭过渡，避免跟手迟滞 */
function startResize(event: MouseEvent): void {
  if (appStore.sidebarCollapsed) {
    return
  }
  const startX = event.clientX
  const startWidth = appStore.sidebarWidth
  resizing.value = true

  const onMove = (moveEvent: MouseEvent): void => {
    appStore.setSidebarWidth(startWidth + moveEvent.clientX - startX)
  }
  const onUp = (): void => {
    resizing.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }

  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
</script>

<template>
  <aside
    class="sidebar"
    :class="{ 'is-collapsed': appStore.sidebarCollapsed, 'is-resizing': resizing }"
    :style="sidebarStyle"
  >
    <div class="sidebar__brand">
      <RouterLink to="/" class="brand-logo" :title="siteStore.siteName" @click="onNavigate">
        <BrandLogo :mode="appStore.sidebarCollapsed ? 'mark' : 'wide'" />
      </RouterLink>

      <button
        v-if="!appStore.sidebarCollapsed"
        class="brand-collapse"
        type="button"
        aria-label="收起侧栏"
        @click="appStore.toggleSidebar()"
      >
        <el-icon><ArrowLeft /></el-icon>
      </button>
    </div>

    <nav class="sidebar__nav">
      <RouterLink
        :to="homeNav.path"
        class="nav-item"
        :class="{ 'is-active': isHome }"
        :title="appStore.sidebarCollapsed ? homeNav.title : undefined"
        @click="onNavigate"
      >
        <el-icon class="nav-item__icon"><component :is="homeNav.icon" /></el-icon>
        <span class="nav-item__text">工作台</span>
      </RouterLink>

      <RouterLink
        v-for="item in unifiedNav"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        :class="{ 'is-active': isActive(item.path) }"
        :title="appStore.sidebarCollapsed ? item.title : undefined"
        @click="onNavigate"
      >
        <el-icon class="nav-item__icon"><component :is="unifiedIcons[item.path]" /></el-icon>
        <span class="nav-item__text">{{ item.title }}</span>
      </RouterLink>

      <div class="nav-divider" />

      <div v-for="group in navGroups" :key="group.key" class="nav-group">
        <button
          class="nav-item nav-group__header"
          :class="{ 'is-open': expanded.includes(group.key), 'is-current': activeGroup === group.key }"
          type="button"
          :title="appStore.sidebarCollapsed ? group.title : undefined"
          @click="toggleGroup(group.key)"
        >
          <el-icon class="nav-item__icon"><component :is="group.icon" /></el-icon>
          <span class="nav-item__text">{{ group.title }}</span>
          <el-icon class="nav-group__arrow"><ArrowRight /></el-icon>
        </button>

        <div v-show="expanded.includes(group.key)" class="nav-group__body">
          <RouterLink
            v-for="item in group.children"
            :key="item.path"
            :to="item.path"
            class="nav-subitem"
            :class="{ 'is-active': isActive(item.path) }"
            @click="onNavigate"
          >
            <span class="nav-subitem__text">{{ item.title }}</span>
          </RouterLink>
        </div>
      </div>

      <div class="nav-divider" />

      <RouterLink
        v-for="link in systemNav"
        :key="link.path"
        :to="link.path"
        class="nav-item"
        :class="{ 'is-active': isActive(link.path) }"
        :title="appStore.sidebarCollapsed ? link.title : undefined"
        @click="onNavigate"
      >
        <el-icon class="nav-item__icon"><component :is="link.icon" /></el-icon>
        <span class="nav-item__text">{{ link.title }}</span>
      </RouterLink>
    </nav>

    <div v-show="!appStore.sidebarCollapsed" class="sidebar__footer">
      <img class="footer-art" :src="lakeImage" alt="" aria-hidden="true" />
      <p class="slogan">让工作更高效<br />让生活更简单</p>
    </div>

    <button
      v-if="appStore.sidebarCollapsed"
      class="sidebar__expand"
      type="button"
      aria-label="展开侧栏"
      @click="appStore.toggleSidebar()"
    >
      <el-icon><ArrowRight /></el-icon>
    </button>

    <span
      class="sidebar__resizer"
      role="separator"
      aria-orientation="vertical"
      title="拖动调整宽度，双击恢复默认"
      @mousedown.prevent="startResize"
      @dblclick="appStore.resetSidebarWidth()"
    />
  </aside>
</template>

<style scoped lang="scss">
.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: 14px 12px 0;
  // 侧栏也做玻璃：半透明渐变 + 背景模糊，让页面底色晕染透上来
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--sidebar-bg-from) 76%, transparent) 0%,
    color-mix(in srgb, var(--sidebar-bg-to) 82%, transparent) 100%
  );
  backdrop-filter: blur(18px) saturate(1.12);
  -webkit-backdrop-filter: blur(18px) saturate(1.12);
  border-right: 1px solid var(--border-color);
  transition: width $duration-base $ease-out;
  z-index: 30;

  &.is-resizing {
    transition: none;
  }

  &.is-collapsed {
    padding-inline: 10px;

    .sidebar__brand {
      justify-content: center;
      padding-inline: 0;
    }

    .brand-logo {
      justify-content: center;
    }

    .nav-item__text,
    .nav-group__arrow,
    .nav-subitem {
      display: none;
    }

    .nav-item {
      justify-content: center;
      height: 40px;
      padding: 0;
      gap: 0;

      &::before {
        display: none;
      }
    }

    .nav-group__body {
      display: none !important;
    }
  }
}

/* 右侧拖拽热区：平时透明，hover 时露出一条细线 */
.sidebar__resizer {
  position: absolute;
  top: 0;
  right: -3px;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 2;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: 2px;
    width: 2px;
    height: 100%;
    background: transparent;
    transition: background $duration-fast $ease-out;
  }

  &:hover::after {
    background: var(--brand-400);
  }
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 18px;
}

.brand-logo {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
}

.brand-collapse {
  @include flex-center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0;
  transition: opacity $duration-fast $ease-out, background $duration-fast $ease-out;

  &:hover {
    background: var(--surface-soft);
    color: var(--brand-600);
  }
}

.sidebar:hover .brand-collapse {
  opacity: 1;
}

.sidebar__expand {
  @include flex-center;
  width: 100%;
  height: 34px;
  margin-top: 6px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;

  &:hover {
    background: var(--surface-soft);
    color: var(--brand-600);
  }
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 2px 0 10px;
  @include scrollbar(4px);

  /* 展开分组后内容会超高，必须禁止子项被挤压，否则整列会被压扁而不是滚动 */
  > * {
    flex: 0 0 auto;
  }
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 40px;
  padding: 0 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13.5px;
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  /* 选中态左侧指示条 */
  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 3px;
    width: 3px;
    height: 15px;
    margin-top: -7.5px;
    border-radius: 3px;
    background: var(--brand-500);
    opacity: 0;
    transform: scaleY(0.4);
    transition:
      opacity $duration-fast $ease-out,
      transform $duration-fast $ease-out;
  }

  &:hover {
    background: var(--surface-soft);
    color: var(--text-primary);

    .nav-item__icon {
      color: var(--text-secondary);
    }
  }

  &.is-active {
    background: color-mix(in srgb, var(--brand-500) 11%, transparent);
    color: var(--brand-700);
    font-weight: 600;

    &::before {
      opacity: 1;
      transform: scaleY(1);
    }

    .nav-item__icon {
      color: var(--brand-500);
    }
  }
}

.nav-item__icon {
  font-size: 17px;
  flex: 0 0 17px;
  color: var(--text-tertiary);
  transition: color $duration-fast $ease-out;
}

.nav-item__text {
  flex: 1;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 分组头：可折叠，含当前页时只染文字不染底色 */
.nav-group__header {
  color: var(--text-secondary);

  &.is-open {
    color: var(--text-primary);

    .nav-item__icon {
      color: var(--text-secondary);
    }
  }

  &.is-current {
    color: var(--brand-600);
    font-weight: 600;

    .nav-item__icon {
      color: var(--brand-500);
    }
  }
}

.nav-group__arrow {
  font-size: 10px;
  color: var(--text-tertiary);
  transition: transform $duration-fast $ease-out;
}

.nav-group__header.is-open .nav-group__arrow {
  transform: rotate(90deg);
}

.nav-group__body {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin: 4px 0 6px;
  padding-left: 26px;

  /* 竖向层级导轨，子项圆点落在导轨上 */
  &::before {
    content: '';
    position: absolute;
    top: 6px;
    bottom: 6px;
    left: 17px;
    width: 1px;
    background: var(--border-strong);
    opacity: 0.7;
  }
}

.nav-subitem {
  position: relative;
  display: flex;
  align-items: center;
  height: 31px;
  padding: 0 8px;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 12.5px;
  transition:
    background $duration-fast $ease-out,
    color $duration-fast $ease-out;

  /* 圆点必须落在导轨上，所以不能裁切子项盒子 */
  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: -10px;
    width: 5px;
    height: 5px;
    margin-top: -2.5px;
    border-radius: 50%;
    background: var(--border-strong);
    transition:
      background $duration-fast $ease-out,
      box-shadow $duration-fast $ease-out;
  }

  &:hover {
    background: var(--surface-soft);
    color: var(--text-primary);

    &::before {
      background: var(--text-tertiary);
    }
  }

  &.is-active {
    background: color-mix(in srgb, var(--brand-500) 10%, transparent);
    color: var(--brand-700);
    font-weight: 600;

    &::before {
      background: var(--brand-500);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand-500) 20%, transparent);
    }
  }
}

.nav-subitem__text {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-divider {
  height: 1px;
  margin: 12px 10px 10px;
  background: linear-gradient(90deg, transparent, var(--border-strong), transparent);
}

.sidebar__footer {
  position: relative;
  height: 152px;
  margin: 0 -12px;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  justify-content: center;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(
      180deg,
      var(--sidebar-bg-to) 0%,
      color-mix(in srgb, var(--sidebar-bg-to) 35%, transparent) 38%,
      color-mix(in srgb, var(--sidebar-bg-to) 90%, transparent) 100%
    );
  }
}

.footer-art {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 24% 58%;
  opacity: 0.72;
  filter: var(--footer-art-filter);
}

.slogan {
  position: relative;
  z-index: 1;
  padding-bottom: 18px;
  font-size: 12px;
  line-height: 1.9;
  letter-spacing: 2.4px;
  color: var(--brand-600);
  text-align: center;
}
</style>
