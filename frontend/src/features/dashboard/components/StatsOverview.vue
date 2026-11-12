<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { ArrowRight, Money, Reading, TrendCharts } from '@element-plus/icons-vue'
import { statsApi, type StatsPoint } from '@/features/stats/api'

const router = useRouter()

const { data, isLoading } = useQuery({
  queryKey: ['stats', 'overview'],
  queryFn: () => statsApi.summary(),
  staleTime: 60_000,
})

const work = computed(() => data.value?.work)
const study = computed(() => data.value?.study)
const life = computed(() => data.value?.life)

function yuan(cents: number | undefined): string {
  return ((cents ?? 0) / 100).toFixed(0)
}

function hours(minutes: number | undefined): string {
  return ((minutes ?? 0) / 60).toFixed(1)
}

function pct(value: number | undefined): string {
  return (value ?? 0).toFixed(1).replace('.0', '')
}

/** 迷你柱：取组内最大值归一化，高度按百分比 */
function bars(points: StatsPoint[] | undefined, pick: (point: StatsPoint) => number): number[] {
  const values = (points ?? []).map(pick)
  const max = Math.max(...values, 1)
  return values.map((value) => Math.max(8, Math.round((value / max) * 100)))
}
</script>

<template>
  <section class="overview">
    <header class="overview__head">
      <h2>数据概览</h2>
      <button type="button" class="overview__more" @click="router.push('/stats')">
        查看完整统计<el-icon><ArrowRight /></el-icon>
      </button>
    </header>

    <div v-if="isLoading" class="overview__loading">
      <el-skeleton :rows="2" animated />
    </div>

    <div v-else class="overview__grid">
      <article class="stat" @click="router.push('/stats')">
        <header class="stat__head">
          <span class="stat__icon is-work"><el-icon><TrendCharts /></el-icon></span>
          <span class="stat__title">工作效率</span>
        </header>
        <div class="stat__value">
          {{ work?.weekDone ?? 0 }}<em>个完成</em>
        </div>
        <div class="stat__meta">
          本周新建 {{ work?.weekCreated ?? 0 }} · 完成率 {{ pct(work?.completionRate) }}%
          <template v-if="(work?.overdue ?? 0) > 0"> · 逾期 {{ work?.overdue }}</template>
        </div>
        <div class="stat__bars">
          <span
            v-for="(height, index) in bars(work?.trend, (point) => point.primary)"
            :key="index"
            class="stat__bar is-work"
            :style="{ height: `${height}%` }"
          />
        </div>
      </article>

      <article class="stat" @click="router.push('/stats')">
        <header class="stat__head">
          <span class="stat__icon is-study"><el-icon><Reading /></el-icon></span>
          <span class="stat__title">学习成长</span>
        </header>
        <div class="stat__value">
          {{ hours(study?.monthMinutes) }}<em>小时 / 本月</em>
        </div>
        <div class="stat__meta">
          连续 {{ study?.streakDays ?? 0 }} 天 · 计划完成 {{ pct(study?.planProgress) }}%
        </div>
        <div class="stat__bars">
          <span
            v-for="(height, index) in bars(study?.trend, (point) => point.primary)"
            :key="index"
            class="stat__bar is-study"
            :style="{ height: `${height}%` }"
          />
        </div>
      </article>

      <article class="stat" @click="router.push('/stats')">
        <header class="stat__head">
          <span class="stat__icon is-life"><el-icon><Money /></el-icon></span>
          <span class="stat__title">生活收支</span>
        </header>
        <div class="stat__value">
          ¥{{ yuan(life?.monthExpense) }}<em>本月支出</em>
        </div>
        <div class="stat__meta">
          收入 ¥{{ yuan(life?.monthIncome) }} · 结余 ¥{{ yuan(life?.balance) }}
        </div>
        <div class="stat__bars">
          <span
            v-for="(height, index) in bars(life?.trend, (point) => point.primary)"
            :key="index"
            class="stat__bar is-life"
            :style="{ height: `${height}%` }"
          />
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped lang="scss">
.overview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  h2 {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }
}

.overview__more {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 11px;
  cursor: pointer;

  &:hover {
    color: var(--brand-600);
  }
}

.overview__loading {
  @include card($radius-lg);
  padding: 18px;
}

.overview__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.stat {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 14px 16px 12px;
  border-radius: 16px;
  background: var(--bg-card);
  border: 1px solid rgba(233, 242, 239, 0.9);
  box-shadow: 0 8px 22px rgba(23, 74, 60, 0.05);
  cursor: pointer;
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 30px rgba(23, 74, 60, 0.09);
  }
}

.stat__head {
  display: flex;
  align-items: center;
  gap: 7px;
}

.stat__icon {
  @include flex-center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  font-size: 13px;

  &.is-work {
    background: var(--c-work-bg);
    color: var(--c-work);
  }

  &.is-study {
    background: var(--c-study-bg);
    color: var(--c-study);
  }

  &.is-life {
    background: var(--c-life-bg);
    color: var(--c-life);
  }
}

.stat__title {
  font-size: 12px;
  font-weight: 600;
  color: #5b6f76;
}

.stat__value {
  margin-top: 8px;
  font-family: var(--font-number);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--text-primary);

  em {
    margin-left: 4px;
    font-size: 10.5px;
    font-style: normal;
    font-weight: 500;
    color: var(--text-tertiary);
  }
}

.stat__meta {
  margin-top: 4px;
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.stat__bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 34px;
  margin-top: 10px;
}

.stat__bar {
  flex: 1;
  border-radius: 2px 2px 1px 1px;
  min-height: 3px;

  &.is-work {
    background: linear-gradient(180deg, #9dc2f8, var(--c-work));
    opacity: 0.85;
  }

  &.is-study {
    background: linear-gradient(180deg, #c3bafc, var(--c-study));
    opacity: 0.85;
  }

  &.is-life {
    background: linear-gradient(180deg, #ffd39a, var(--c-life));
    opacity: 0.85;
  }
}

@media (max-width: 991px) {
  .overview__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
