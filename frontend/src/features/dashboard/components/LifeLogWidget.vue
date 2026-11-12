<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'

export interface LifeLogPreview {
  id: string
  title: string
  desc: string
  date: string
  cover?: string | null
}

interface Props {
  items: LifeLogPreview[]
}

const props = defineProps<Props>()
const emit = defineEmits<{ viewAll: []; add: [] }>()

const gradients = [
  ['#8FD8C4', '#3E9E8C'],
  ['#F6C28B', '#D98F5A'],
]

function gradient(index: number): string {
  const pair = gradients[index % gradients.length] ?? gradients[0]!
  return `linear-gradient(145deg, ${pair[0]}, ${pair[1]})`
}
</script>

<template>
  <section class="widget">
    <header class="widget__head">
      <h3>
        <svg class="widget__icon" viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
          <path d="M8 13.4C4.2 10.8 1.6 8.6 1.6 5.9 1.6 3.9 3.2 2.4 5 2.4c1.2 0 2.3.6 3 1.6.7-1 1.8-1.6 3-1.6 1.8 0 3.4 1.5 3.4 3.5 0 2.7-2.6 4.9-6.4 7.5Z" fill="#F2789F" />
        </svg>
        生活记录
      </h3>
      <button class="widget__more" type="button" @click="emit('viewAll')">
        查看全部<el-icon><ArrowRight /></el-icon>
      </button>
    </header>

    <ul v-if="props.items.length" class="logs">
      <li v-for="(log, index) in props.items" :key="log.id" class="logs__item">
        <span v-if="log.cover" class="logs__thumb">
          <img :src="log.cover" :alt="log.title" loading="lazy" />
        </span>
        <span v-else class="logs__thumb" :style="{ background: gradient(index) }" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="#fff" stroke-width="1.3">
            <rect x="3" y="5" width="18" height="14" rx="3" opacity=".9" />
            <circle cx="9" cy="10" r="1.6" fill="#fff" stroke="none" />
            <path d="M4 17l5-4 4 3 3-2 4 3" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </span>

        <div class="logs__body">
          <div class="logs__row">
            <span class="logs__title">{{ log.title }}</span>
            <time class="logs__date">{{ log.date }}</time>
          </div>
          <p class="logs__desc">{{ log.desc }}</p>
        </div>
      </li>
    </ul>

    <div v-else class="logs__empty">
      <p>还没有生活记录</p>
      <button type="button" @click="emit('add')">+ 写一条</button>
    </div>

    <button class="logs__add" type="button" @click="emit('add')">记录今天的生活</button>
  </section>
</template>

<style scoped lang="scss">
.widget {
  display: flex;
  flex-direction: column;
  min-height: 286px;
  padding: 15px 16px 12px;
  border-radius: 18px;
  background: var(--bg-card);
  border: 1px solid rgba(233, 242, 239, 0.9);
  box-shadow: 0 10px 26px rgba(23, 74, 60, 0.05);
}

.widget__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  h3 {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13.5px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.widget__icon {
  flex: 0 0 15px;
}

.widget__more {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;

  &:hover {
    color: var(--brand-600);
  }
}

.logs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.logs__item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.logs__thumb {
  @include flex-center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 10px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.logs__body {
  flex: 1;
  min-width: 0;
}

.logs__row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.logs__title {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-primary);
  @include ellipsis(1);
}

.logs__date {
  font-family: var(--font-number);
  font-size: 10px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.logs__desc {
  margin-top: 3px;
  font-size: 10.5px;
  color: var(--text-tertiary);
  @include ellipsis(1);
}

.logs__empty {
  flex: 1;
  @include flex-center;
  flex-direction: column;
  gap: 10px;
  font-size: 12px;
  color: var(--text-tertiary);

  button {
    padding: 5px 12px;
    border: 1px dashed var(--border-strong);
    border-radius: 999px;
    background: transparent;
    color: var(--brand-600);
    font-size: 11.5px;
    cursor: pointer;
  }
}

.logs__add {
  margin-top: auto;
  padding: 8px 0 0;
  border: none;
  border-top: 1px dashed var(--border-color);
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  text-align: right;
  cursor: pointer;
  width: 100%;

  &:hover {
    color: var(--brand-600);
  }
}
</style>
