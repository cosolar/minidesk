<script setup lang="ts">
import { ArrowRight, CircleCheck } from '@element-plus/icons-vue'

export interface TodoPreview {
  id: string
  title: string
  time: string
  done: boolean
  highlight?: boolean
}

interface Props {
  items: TodoPreview[]
  total: number
  done: number
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), { loading: false })
const emit = defineEmits<{ toggle: [id: string, done: boolean]; add: []; viewAll: [] }>()
</script>

<template>
  <section class="widget">
    <header class="widget__head">
      <h3>
        <el-icon class="widget__icon is-todo"><CircleCheck /></el-icon>
        今日待办
      </h3>
      <button class="widget__more" type="button" @click="emit('viewAll')">
        查看全部<el-icon><ArrowRight /></el-icon>
      </button>
    </header>

    <el-skeleton v-if="loading" :rows="4" animated />

    <ul v-else-if="items.length" class="todo">
      <li
        v-for="item in items"
        :key="item.id"
        class="todo__item"
        :class="{ 'is-done': item.done, 'is-highlight': item.highlight && !item.done }"
      >
        <button
          class="todo__check"
          type="button"
          :aria-pressed="item.done"
          :aria-label="item.title"
          @click="emit('toggle', item.id, !item.done)"
        >
          <svg v-if="item.done" viewBox="0 0 16 16" width="10" height="10" aria-hidden="true">
            <path
              d="M3.5 8.4 6.4 11 12.5 4.9"
              fill="none"
              stroke="currentColor"
              stroke-width="2.1"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <span class="todo__title">{{ item.title }}</span>
        <time class="todo__time">{{ item.time }}</time>
      </li>
    </ul>

    <div v-else class="todo__empty">
      <p>今天还没有待办</p>
      <button type="button" @click="emit('add')">+ 添加一条</button>
    </div>

    <footer v-if="items.length" class="widget__foot">
      已完成 {{ props.done }} / {{ props.total }}
    </footer>
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
  margin-bottom: 8px;

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
  font-size: 15px;

  &.is-todo {
    color: var(--brand-500);
  }
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
  transition: color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
  }
}

.todo {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.todo__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 6px;
  border-radius: 10px;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-subtle);
  }

  &.is-highlight .todo__check {
    border-color: var(--brand-400);
  }

  &.is-done {
    .todo__title {
      color: var(--text-tertiary);
    }

    .todo__time {
      color: var(--text-tertiary);
    }
  }
}

.todo__check {
  @include flex-center;
  width: 17px;
  height: 17px;
  flex: 0 0 17px;
  padding: 0;
  border-radius: 6px;
  border: 1.5px solid var(--border-strong);
  background: transparent;
  color: #fff;
  cursor: pointer;
  transition:
    background $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  .is-done & {
    background: var(--brand-500);
    border-color: var(--brand-500);
  }
}

.todo__title {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  color: var(--text-primary);
  @include ellipsis(1);
}

.is-done .todo__title {
  text-decoration: line-through;
}

.todo__time {
  font-family: var(--font-number);
  font-size: 10.5px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.todo__empty {
  flex: 1;
  @include flex-center;
  flex-direction: column;
  gap: 10px;
  color: var(--text-tertiary);
  font-size: 12px;

  button {
    padding: 5px 12px;
    border: 1px dashed var(--border-strong);
    border-radius: 999px;
    background: transparent;
    color: var(--brand-600);
    font-size: 11.5px;
    cursor: pointer;

    &:hover {
      background: var(--brand-50);
    }
  }
}

.widget__foot {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--border-color);
  font-size: 10px;
  color: var(--text-tertiary);
  text-align: right;
}
</style>
