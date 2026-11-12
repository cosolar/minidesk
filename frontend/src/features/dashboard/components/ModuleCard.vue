<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { ArrowRight } from '@element-plus/icons-vue'
import type { AppEntry, ModuleConfig } from '../data'

const props = defineProps<{ mod: ModuleConfig }>()
const emit = defineEmits<{ enter: [key: string]; open: [app: AppEntry] }>()

/** 主题只传两个色值，渐变终点由 CSS 决定（暗色主题要落到 --bg-card） */
const headerStyle = {
  '--head-from': props.mod.bannerFrom,
  '--head-to': props.mod.bannerTo,
}

const particles = [`${props.mod.accent}24`, `${props.mod.accent}18`, `${props.mod.accent}10`]
</script>

<template>
  <article class="module">
    <header class="module__head" :style="headerStyle">
      <span class="module__icon" :style="{ color: mod.accent }">
        <Icon :icon="mod.bannerIcon" width="22" />
      </span>

      <div class="module__titles">
        <h2>{{ mod.title }}</h2>
        <p>{{ mod.subtitle }}</p>
      </div>

      <button class="module__enter" type="button" :style="{ color: mod.accent }" @click="emit('enter', mod.key)">
        进入{{ mod.title }}
        <el-icon><ArrowRight /></el-icon>
      </button>

      <span class="module__deco" :style="{ color: mod.accent }" aria-hidden="true">
        <Icon :icon="mod.bannerIcon" width="72" />
      </span>
      <span class="module__orb module__orb--a" :style="{ background: particles[0] }" aria-hidden="true" />
      <span class="module__orb module__orb--b" :style="{ background: particles[1] }" aria-hidden="true" />
    </header>

    <div class="module__grid" :style="{ '--cols': mod.apps.length > 6 ? 4 : 3 }">
      <button
        v-for="app in mod.apps"
        :key="app.code"
        class="app-item"
        type="button"
        @click="emit('open', app)"
      >
        <span class="app-item__tile" :style="{ '--tile-bg': app.bg, '--tile-fg': app.color }">
          <Icon :icon="app.icon" width="20" />
        </span>
        <span class="app-item__name">{{ app.name }}</span>
      </button>
    </div>
  </article>
</template>

<style scoped lang="scss">
.module {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition:
    transform $duration-base $ease-out,
    box-shadow $duration-base $ease-out;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-card-hover);
  }
}

.module__head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 66px;
  padding: 0 16px;
  overflow: hidden;
  background: linear-gradient(180deg, var(--head-from) 0%, var(--head-to) 62%, var(--bg-card) 100%);
}


.module__icon {
  @include flex-center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: var(--surface-glass);
  box-shadow: 0 4px 10px rgba(23, 74, 60, 0.06);
  flex: 0 0 38px;
  z-index: 1;
}

.module__titles {
  position: relative;
  z-index: 1;
  min-width: 0;

  h2 {
    font-family: var(--font-display);
    font-size: 15.5px;
    font-weight: 700;
    letter-spacing: 0.3px;
    color: var(--text-primary);
  }

  p {
    margin-top: 1px;
    font-size: 10.5px;
    letter-spacing: 0.2px;
    color: var(--text-secondary);
    @include ellipsis(1);
  }
}

.module__enter {
  position: relative;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 1px;
  margin-left: auto;
  padding: 4px 9px;
  border: none;
  border-radius: 999px;
  background: var(--surface-soft);
  font-size: 10.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    background: var(--bg-card);
    box-shadow: 0 4px 12px rgba(23, 74, 60, 0.08);
  }
}

.module__deco {
  position: absolute;
  right: -6px;
  top: 14px;
  opacity: 0.1;
  transform: rotate(-6deg);
  pointer-events: none;
}

.module__orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;

  &--a {
    width: 92px;
    height: 92px;
    right: -26px;
    top: -34px;
    filter: blur(2px);
  }

  &--b {
    width: 48px;
    height: 48px;
    right: 68px;
    bottom: -22px;
  }
}

.module__grid {
  /* 按条目数决定每行列数：8 个走 4 列，6 个走 3 列，两种都能填满两行 */
  display: flex;
  flex-wrap: wrap;
  padding: 12px 8px 14px;
}

.app-item {
  flex: 0 0 calc(100% / var(--cols, 4));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  padding: 8px 2px;
  border: none;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background $duration-fast $ease-out, transform $duration-fast $ease-out;

  &:hover {
    background: var(--bg-subtle);
    transform: translateY(-1px);
  }
}

.app-item__tile {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: var(--tile-bg);
  color: var(--tile-fg);
}


.app-item__name {
  font-size: 11.5px;
  color: var(--text-secondary);
  white-space: nowrap;
  letter-spacing: 0.1px;
}
</style>
