<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { useAppStore } from '@/core/stores/app'
import type { ThemeId } from '@/core/theme/catalog'

const props = withDefaults(defineProps<{ columns?: 2 | 3 | 5; modelValue?: ThemeId }>(), {
  columns: 2,
})
const emit = defineEmits<{ 'update:modelValue': [ThemeId] }>()

const appStore = useAppStore()

/** 有 v-model 时以它为选中态，否则直接跟随全局主题 */
const active = computed(() => props.modelValue ?? appStore.theme)

function choose(id: ThemeId): void {
  // 主题始终立即生效；v-model 只是把选择同步给表单，便于「保存偏好」再落库
  appStore.setTheme(id)
  emit('update:modelValue', id)
}
</script>

<template>
  <div class="theme-picker" :style="{ '--cols': columns }">
    <button
      v-for="item in appStore.themes"
      :key="item.id"
      type="button"
      class="theme-item"
      :class="{ 'is-active': active === item.id }"
      :aria-pressed="active === item.id"
      @click="choose(item.id)"
    >
      <span class="theme-item__preview" :style="{ background: item.swatch }">
        <span class="theme-item__sidebar" />
        <span class="theme-item__chip" />
        <span class="theme-item__chip theme-item__chip--wide" />
      </span>

      <span class="theme-item__meta">
        <span class="theme-item__name">{{ item.label }}</span>
        <span class="theme-item__tag">{{ item.tagline }}</span>
      </span>

      <span v-if="active === item.id" class="theme-item__check">
        <el-icon><Check /></el-icon>
      </span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.theme-picker {
  display: grid;
  grid-template-columns: repeat(var(--cols, 2), minmax(0, 1fr));
  gap: 10px;

  @include respond-below(sm) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.theme-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 7px;
  border: 1px solid var(--border-color);
  border-radius: 13px;
  background: var(--bg-card);
  text-align: left;
  cursor: pointer;
  transition:
    border-color $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out,
    transform $duration-fast $ease-out;

  &:hover {
    border-color: var(--brand-200);
    box-shadow: 0 8px 20px rgba(23, 74, 60, 0.08);
    transform: translateY(-1px);
  }

  &.is-active {
    border-color: var(--brand-500);
    box-shadow: 0 0 0 2px var(--brand-100);
  }
}

.theme-item__preview {
  position: relative;
  display: block;
  height: 52px;
  border-radius: 9px;
  overflow: hidden;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.28),
    0 2px 6px rgba(23, 74, 60, 0.06);
}

/* 三笔示意：左窄条=侧栏，两条圆角块=卡片 */
.theme-item__sidebar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 18%;
  background: rgba(255, 255, 255, 0.34);
}

.theme-item__chip {
  position: absolute;
  left: 26%;
  top: 30%;
  width: 30%;
  height: 9px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 1px 3px rgba(15, 35, 45, 0.1);
}

.theme-item__chip--wide {
  left: 26%;
  top: 56%;
  width: 52%;
  height: 7px;
  opacity: 0.62;
}

.theme-item__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 2px 2px;
}

.theme-item__name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text-primary);
}

.theme-item__tag {
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--text-tertiary);
  /* 固定两行高度：卡片等高，网格不会参差 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  min-height: 2.9em;
}

.theme-item__check {
  position: absolute;
  top: 11px;
  right: 11px;
  @include flex-center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand-500);
  color: #fff;
  font-size: 11px;
  box-shadow: 0 2px 8px rgba(23, 74, 60, 0.24);
}
</style>
