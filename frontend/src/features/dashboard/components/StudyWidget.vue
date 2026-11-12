<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, DataLine } from '@element-plus/icons-vue'

interface Props {
  totalMinutes: number
  targetCompletion: number
  growthRate: number | null
  days: Array<{ date: string; weekday: string; minutes: number }>
}

const props = defineProps<Props>()
const emit = defineEmits<{ viewAll: [] }>()

const percent = computed(() => Math.min(100, Math.max(0, Math.round(props.targetCompletion * 100))))
const hours = computed(() => (props.totalMinutes / 60).toFixed(1))
const maxMinutes = computed(() => Math.max(...props.days.map((day) => day.minutes), 1))
const hasData = computed(() => props.days.some((day) => day.minutes > 0))

function barHeight(minutes: number): string {
  if (minutes <= 0) {
    return '6%'
  }
  return `${Math.max(12, Math.round((minutes / maxMinutes.value) * 100))}%`
}
</script>

<template>
  <section class="widget">
    <header class="widget__head">
      <h3>
        <el-icon class="widget__icon"><DataLine /></el-icon>
        学习进度
      </h3>
      <button class="widget__more" type="button" @click="emit('viewAll')">
        查看详情<el-icon><ArrowRight /></el-icon>
      </button>
    </header>

    <div class="study__top">
      <div
        class="ring"
        :style="{
          background: `conic-gradient(from 200deg, var(--brand-400) 0deg, var(--brand-500) ${percent * 3.6}deg, var(--bg-subtle) ${percent * 3.6}deg)`,
        }"
        role="img"
        :aria-label="`本周学习目标完成 ${percent}%`"
      >
        <div class="ring__inner">
          <strong>{{ percent }}%</strong>
        </div>
      </div>

      <div class="study__info">
        <span class="study__label">学习时长</span>
        <span class="study__hours">{{ hours }}<em>小时</em></span>
        <span v-if="growthRate !== null" class="study__growth">
          <svg viewBox="0 0 10 10" width="8" height="8" aria-hidden="true">
            <path d="M2 7.5 5 3.4l3 4.1" fill="none" stroke="currentColor" stroke-width="1.6" />
          </svg>
          较上周 {{ growthRate >= 0 ? '+' : '' }}{{ Math.round(growthRate * 100) }}%
        </span>
        <span v-else class="study__growth is-muted">较上周 --</span>
      </div>
    </div>

    <div class="bars">
      <div v-for="day in days" :key="day.date" class="bars__col" :title="`${day.date} · ${day.minutes} 分钟`">
        <div class="bars__track">
          <div class="bars__fill" :style="{ height: barHeight(day.minutes) }" />
        </div>
        <span class="bars__label">{{ day.weekday }}</span>
      </div>
    </div>

    <p v-if="!hasData" class="study__hint">还没有学习记录，记一笔就会出现在这里。</p>
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
  margin-bottom: 6px;

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
  color: var(--c-work);
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

.study__top {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 10px;
}

.ring {
  position: relative;
  @include flex-center;
  width: 84px;
  height: 84px;
  flex: 0 0 84px;
  border-radius: 50%;

  &::before {
    content: '';
    position: absolute;
    inset: 10px;
    border-radius: 50%;
    background: var(--bg-card);
  }
}

.ring__inner {
  position: relative;
  text-align: center;

  strong {
    font-family: var(--font-number);
    font-size: 19px;
    font-weight: 700;
    color: var(--text-primary);
    letter-spacing: 0.3px;
  }
}

.study__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.study__label {
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.study__hours {
  font-family: var(--font-number);
  font-size: 21px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-primary);

  em {
    margin-left: 3px;
    font-size: 10.5px;
    font-style: normal;
    font-weight: 500;
    color: var(--text-secondary);
  }
}

.study__growth {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 3px;
  padding: 1px 6px 1px 4px;
  border-radius: 6px;
  background: var(--brand-100);
  color: var(--brand-600);
  font-size: 9.5px;
  width: fit-content;

  &.is-muted {
    background: var(--bg-subtle);
    color: var(--text-tertiary);
  }
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 7px;
  height: 84px;
  margin-top: auto;
  padding: 4px 2px 0;
}

.bars__col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  flex: 1;
  height: 100%;
}

.bars__track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 100%;
  border-radius: 4px;
  background: var(--bg-subtle);
  overflow: hidden;
}

.bars__fill {
  width: 100%;
  border-radius: 4px;
  background: linear-gradient(180deg, var(--brand-400), var(--brand-500));
  transition: height 600ms $ease-out;
}

.bars__label {
  font-size: 9px;
  color: var(--text-tertiary);
}

.study__hint {
  margin-top: 6px;
  font-size: 10px;
  color: var(--text-tertiary);
  text-align: center;
}
</style>
