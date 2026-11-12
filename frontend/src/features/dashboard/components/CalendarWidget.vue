<script setup lang="ts">
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

const emit = defineEmits<{ viewAll: [] }>()

const props = defineProps<{ eventDates?: string[] }>()

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']
const cursor = ref(dayjs())
const selected = ref(dayjs())

const monthTitle = computed(() => cursor.value.format('YYYY年M月'))
const eventSet = computed(() => new Set(props.eventDates ?? []))

const cells = computed(() => {
  const first = cursor.value.startOf('month')
  const offset = (first.day() + 6) % 7
  const start = first.subtract(offset, 'day')
  const marked = eventSet.value

  return Array.from({ length: 42 }, (_, index) => {
    const date = start.add(index, 'day')
    return {
      key: date.format('YYYY-MM-DD'),
      day: date.date(),
      inMonth: date.month() === cursor.value.month(),
      isToday: date.isSame(dayjs(), 'day'),
      hasEvent: marked.has(date.format('YYYY-MM-DD')),
    }
  })
})

function shiftMonth(step: number): void {
  cursor.value = cursor.value.add(step, 'month')
}

function pick(date: string): void {
  selected.value = dayjs(date)
}
</script>

<template>
  <section class="widget">
    <header class="widget__head">
      <h3>日历</h3>
      <div class="cal__nav">
        <button type="button" aria-label="上个月" @click="shiftMonth(-1)">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <span class="cal__month">{{ monthTitle }}</span>
        <button type="button" aria-label="下个月" @click="shiftMonth(1)">
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
    </header>

    <div class="cal__week">
      <span v-for="label in weekLabels" :key="label">{{ label }}</span>
    </div>

    <div class="cal__grid">
      <button
        v-for="cell in cells"
        :key="cell.key"
        type="button"
        class="cal__cell"
        :class="{
          'is-muted': !cell.inMonth,
          'is-today': cell.isToday,
          'is-selected': selected.format('YYYY-MM-DD') === cell.key,
        }"
        @click="pick(cell.key)"
      >
        <span class="cal__day">{{ cell.day }}</span>
        <span v-if="cell.hasEvent" class="cal__dot" />
      </button>
    </div>

    <button class="cal__more" type="button" @click="emit('viewAll')">打开完整日历</button>
  </section>
</template>

<style scoped lang="scss">
.widget {
  display: flex;
  flex-direction: column;
  min-height: 286px;
  padding: 15px 14px 12px;
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
    font-size: 13.5px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.cal__nav {
  display: inline-flex;
  align-items: center;
  gap: 2px;

  button {
    @include flex-center;
    width: 18px;
    height: 18px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--text-tertiary);
    cursor: pointer;

    &:hover {
      background: var(--bg-subtle);
      color: var(--brand-600);
    }
  }
}

.cal__month {
  font-size: 10px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.cal__week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding-bottom: 5px;

  span {
    font-size: 9px;
    color: var(--text-tertiary);
    text-align: center;
  }
}

.cal__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 1px;
  flex: 1;
}

.cal__cell {
  position: relative;
  @include flex-center;
  flex-direction: column;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-subtle);
  }

  &.is-muted .cal__day {
    color: var(--text-tertiary);
  }

  &.is-selected {
    background: var(--brand-50);
  }

  &.is-today .cal__day {
    @include flex-center;
    width: 19px;
    height: 19px;
    border-radius: 50%;
    background: var(--brand-500);
    color: #fff;
    font-weight: 600;
  }
}

.cal__day {
  font-family: var(--font-number);
  font-size: 10.5px;
  line-height: 1;
  color: var(--text-secondary);
}

.cal__dot {
  position: absolute;
  bottom: 2px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--brand-400);
}

.cal__more {
  margin-top: 6px;
  padding: 7px 0 0;
  border: none;
  border-top: 1px dashed var(--border-color);
  background: transparent;
  color: var(--text-tertiary);
  font-size: 10.5px;
  cursor: pointer;

  &:hover {
    color: var(--brand-600);
  }
}
</style>
