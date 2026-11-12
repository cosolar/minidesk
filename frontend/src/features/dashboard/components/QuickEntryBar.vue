<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { Lightning } from '@element-plus/icons-vue'
import { quickEntries } from '../data'

const emit = defineEmits<{ open: [code: string] }>()
</script>

<template>
  <section class="quick">
    <div class="quick__label">
      <span class="quick__label-badge">
        <el-icon><Lightning /></el-icon>
      </span>
      <div class="quick__label-text">
        <strong>快速入口</strong>
        <small>一步直达</small>
      </div>
    </div>

    <div class="quick__items">
      <button
        v-for="entry in quickEntries"
        :key="entry.code"
        class="quick__item"
        type="button"
        @click="emit('open', entry.code)"
      >
        <span class="quick__tile">
          <Icon :icon="entry.icon" width="21" />
        </span>
        <span class="quick__name">{{ entry.name }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.quick {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 22px;
  border-radius: 18px;
  background: var(--surface-glass-strong);
  border: 1px solid var(--glass-border);
  box-shadow: 0 12px 30px rgba(23, 74, 60, 0.08);
  backdrop-filter: blur(12px);

  @include respond-below(sm) {
    padding: 12px 14px;
    gap: 10px;
  }
}

.quick__label {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 18px;
  border-right: 1px solid var(--border-color);
}

.quick__label-badge {
  @include flex-center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: var(--brand-100);
  color: var(--brand-600);
  font-size: 16px;
}

.quick__label-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;

  strong {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }

  small {
    margin-top: 2px;
    font-size: 9.5px;
    letter-spacing: 0.4px;
    color: var(--text-tertiary);
  }

  @include respond-below(sm) {
    display: none;
  }
}

.quick__items {
  display: flex;
  align-items: center;
  flex: 1;
  justify-content: space-around;
  gap: 4px;
  overflow-x: auto;
  @include scrollbar(3px);
}

.quick__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  flex: 0 0 auto;
  min-width: 72px;
  padding: 2px 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 14px;
  transition: transform $duration-fast $ease-out;

  &:hover {
    transform: translateY(-3px);

    .quick__tile {
      box-shadow: 0 10px 20px rgba(23, 74, 60, 0.16);
    }
  }
}

.quick__tile {
  @include flex-center;
  width: 44px;
  height: 44px;
  border-radius: 15px;
  /* 快捷入口统一走主题色：换主题时整排图标一起变，不会留下不合群的色块 */
  background: var(--brand-50);
  color: var(--brand-600);
  transition: box-shadow $duration-fast $ease-out;
}

.quick__name {
  font-size: 11.5px;
  color: var(--text-secondary);
  white-space: nowrap;
}
</style>
