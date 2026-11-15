<script setup lang="ts">
import { ref } from 'vue'
import {
  Delete,
  EditPen,
  MoreFilled,
  StarFilled,
  Top,
  TopRight,
} from '@element-plus/icons-vue'
import type { BookmarkSite } from '../api'

interface Props {
  site: BookmarkSite
  /** 卡片描边与图标渐变的取色，由父级按站点/分类色解析 */
  accent: string
  /** 紧凑模式用于热门推荐，隐藏访问信息 */
  compact?: boolean
  draggable?: boolean
  dragging?: boolean
  dragover?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  compact: false,
  draggable: false,
  dragging: false,
  dragover: false,
})

const emit = defineEmits<{
  open: []
  pin: []
  edit: []
  remove: []
  dragstart: [event: DragEvent]
  dragover: [event: DragEvent]
  drop: [event: DragEvent]
  dragend: []
}>()

/** 移动端没有 hover，操作按钮收进「更多」浮层，避免挤占卡片文字 */
const opsOpen = ref(false)

function toggleOps(): void {
  opsOpen.value = !opsOpen.value
}

function closeOps(): void {
  opsOpen.value = false
}

function hostOf(url: string): string {
  return url.replace(/^https?:\/\//, '').replace(/\/.*$/, '')
}

function initialOf(name: string): string {
  return name.trim().slice(0, 1).toUpperCase() || 'W'
}

function onIconError(event: Event): void {
  const target = event.target as HTMLImageElement | null
  if (target) {
    target.style.display = 'none'
  }
}

function onOpen(): void {
  closeOps()
  emit('open')
}
</script>

<template>
  <article
    class="site-card"
    :class="{
      'is-compact': props.compact,
      'is-dragging': props.dragging,
      'is-dragover': props.dragover,
    }"
    :style="{ '--accent': props.accent }"
    :draggable="props.draggable"
    @click="onOpen"
    @dragstart="emit('dragstart', $event)"
    @dragover="emit('dragover', $event)"
    @drop="emit('drop', $event)"
    @dragend="emit('dragend')"
  >
    <span class="site-card__logo">
      <img v-if="props.site.icon" :src="props.site.icon" alt="" @error="onIconError" />
      <span v-else>{{ initialOf(props.site.name) }}</span>
    </span>

    <div class="site-card__body">
      <div class="site-card__title">
        <h3>{{ props.site.name }}</h3>
        <el-icon v-if="props.site.pinned" class="site-card__pin"><StarFilled /></el-icon>
      </div>
      <p class="site-card__desc">{{ props.site.description || hostOf(props.site.url) }}</p>
      <p v-if="!props.compact" class="site-card__meta">
        <span>{{ hostOf(props.site.url) }}</span>
        <span v-if="props.site.clickCount">{{ props.site.clickCount }} 次</span>
      </p>
    </div>

    <el-icon class="site-card__go"><TopRight /></el-icon>

    <button
      type="button"
      class="site-card__more"
      :class="{ 'is-open': opsOpen }"
      title="更多操作"
      @click.stop="toggleOps"
    >
      <el-icon><MoreFilled /></el-icon>
    </button>

    <div class="site-card__ops" :class="{ 'is-open': opsOpen }" @click.stop>
      <button type="button" :title="props.site.pinned ? '取消置顶' : '置顶'" @click.stop="emit('pin'); closeOps()">
        <el-icon><Top /></el-icon>
      </button>
      <button type="button" title="编辑" @click.stop="emit('edit'); closeOps()">
        <el-icon><EditPen /></el-icon>
      </button>
      <button type="button" title="删除" @click.stop="emit('remove'); closeOps()">
        <el-icon><Delete /></el-icon>
      </button>
    </div>
  </article>
</template>

<style scoped lang="scss">
.site-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--bg-card);
  cursor: pointer;
  overflow: hidden;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: radial-gradient(
      190px 90px at 0% 0%,
      color-mix(in srgb, var(--accent) 10%, transparent),
      transparent 76%
    );
    opacity: 0;
    transition: opacity $duration-fast $ease-out;
    pointer-events: none;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: color-mix(in srgb, var(--accent) 32%, var(--border-color));
    box-shadow: var(--shadow-card-hover);

    &::before {
      opacity: 1;
    }

    .site-card__go {
      opacity: 1;
      transform: translate(0, 0);
    }

    .site-card__ops {
      opacity: 1;
      pointer-events: auto;
    }
  }

  &.is-dragging {
    opacity: 0.45;
  }

  &.is-dragover {
    border-color: var(--accent);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 24%, transparent);
  }

  &__logo {
    @include flex-center;
    position: relative;
    flex: 0 0 auto;
    width: 36px;
    height: 36px;
    border-radius: 11px;
    color: #fff;
    font-size: 15px;
    font-weight: 700;
    background: linear-gradient(
      140deg,
      color-mix(in srgb, var(--accent) 84%, #fff),
      color-mix(in srgb, var(--accent) 90%, #000)
    );
    box-shadow: 0 6px 14px color-mix(in srgb, var(--accent) 24%, transparent);
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  &__body {
    position: relative;
    flex: 1;
    min-width: 0;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 5px;

    h3 {
      font-size: 12.8px;
      font-weight: 600;
      color: var(--text-primary);
      @include ellipsis(1);
    }
  }

  &__pin {
    flex: 0 0 auto;
    color: #ff9f43;
    font-size: 11px;
  }

  &__desc {
    margin-top: 2px;
    font-size: 11px;
    line-height: 1.45;
    color: var(--text-tertiary);
    @include ellipsis(1);
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 4px;
    font-size: 10px;
    color: var(--text-tertiary);
    opacity: 0.85;

    span:first-child {
      max-width: 66%;
      @include ellipsis(1);
    }
  }

  &__go {
    position: absolute;
    top: 10px;
    right: 11px;
    color: var(--text-tertiary);
    font-size: 12px;
    opacity: 0;
    transform: translate(-3px, 3px);
    transition: opacity $duration-fast $ease-out, transform $duration-fast $ease-out;
  }

  &__ops {
    position: absolute;
    top: 7px;
    right: 7px;
    display: flex;
    gap: 1px;
    padding: 2px;
    border-radius: 9px;
    background: color-mix(in srgb, var(--bg-card) 94%, transparent);
    box-shadow: var(--shadow-card);
    opacity: 0;
    pointer-events: none;
    transition: opacity $duration-fast $ease-out;

    button {
      @include flex-center;
      width: 24px;
      height: 24px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: var(--text-tertiary);
      font-size: 12px;
      cursor: pointer;

      &:hover {
        color: var(--brand-600);
        background: var(--bg-hover);
      }
    }
  }

  &__more {
    @include flex-center;
    display: none;
    position: absolute;
    top: 8px;
    right: 8px;
    width: 25px;
    height: 25px;
    border: 0;
    border-radius: 8px;
    background: var(--bg-hover);
    color: var(--text-tertiary);
    font-size: 14px;
    cursor: pointer;
    z-index: 3;

    &.is-open {
      color: var(--brand-600);
      background: color-mix(in srgb, var(--brand-500) 14%, var(--bg-card));
    }
  }

  // 紧凑卡片用于热门推荐：图标更小、去掉访问信息
  &.is-compact {
    padding: 10px 11px;
    gap: 9px;

    .site-card__logo {
      width: 30px;
      height: 30px;
      border-radius: 9px;
      font-size: 13px;
    }

    .site-card__title h3 {
      font-size: 12px;
    }

    .site-card__desc {
      font-size: 10.5px;
    }
  }
}

@include respond-below(sm) {
  .site-card {
    &__go {
      display: none;
    }

    &__more {
      display: flex;
    }

    &__ops {
      top: 38px;
      right: 8px;
      z-index: 4;
    }

    &__ops.is-open {
      opacity: 1;
      pointer-events: auto;
    }
  }
}
</style>
