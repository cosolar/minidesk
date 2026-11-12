<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Money, Reading, TrendCharts } from '@element-plus/icons-vue'
import { statsApi, type StatsPoint } from '../api'

const { data, isLoading } = useQuery({
  queryKey: ['stats', 'summary'],
  queryFn: () => statsApi.summary(),
  staleTime: 60_000,
})

const work = computed(() => data.value?.work)
const study = computed(() => data.value?.study)
const life = computed(() => data.value?.life)

function yuan(cents: number | undefined): string {
  return ((cents ?? 0) / 100).toFixed(2)
}

function hours(minutes: number | undefined): string {
  return ((minutes ?? 0) / 60).toFixed(1)
}

/** 柱状图归一化：取组内最大值，留 10% 顶部空间 */
function ratio(value: number, points: StatsPoint[] | undefined, secondary = false): number {
  const max = Math.max(
    ...(points ?? []).map((point) => Math.max(point.primary, secondary ? point.secondary : 0)),
    1,
  )
  return Math.max(3, Math.round((value / max) * 100))
}

const lifeMax = computed(() => {
  const points = life.value?.trend ?? []
  return Math.max(...points.map((point) => Math.max(point.primary, point.secondary)), 1)
})

function lifeRatio(value: number): number {
  return Math.max(2, Math.round((value / lifeMax.value) * 100))
}
</script>

<template>
  <section class="stats-page">
    <header class="page-head">
      <div>
        <h1>数据统计</h1>
        <p class="page-head__sub">工作效率、学习成长、生活收支，三个维度看长期趋势</p>
      </div>
      <span class="page-head__range">近 8 周 / 近 6 个月</span>
    </header>

    <div v-if="isLoading" class="stats-loading">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-else class="stats-grid">
      <!-- 工作效率 -->
      <section class="stats-card">
        <header class="stats-card__head">
          <span class="stats-card__icon is-work"><el-icon><TrendCharts /></el-icon></span>
          <div>
            <h2>工作效率</h2>
            <p>按周统计完成与新建</p>
          </div>
        </header>

        <div class="kpis">
          <div class="kpi">
            <span>本周完成</span>
            <strong>{{ work?.weekDone ?? 0 }}</strong>
          </div>
          <div class="kpi">
            <span>本周新建</span>
            <strong>{{ work?.weekCreated ?? 0 }}</strong>
          </div>
          <div class="kpi">
            <span>近 30 天完成率</span>
            <strong>{{ (work?.completionRate ?? 0).toFixed(1).replace('.0', '') }}<em>%</em></strong>
          </div>
          <div class="kpi is-warn">
            <span>逾期未完成</span>
            <strong>{{ work?.overdue ?? 0 }}</strong>
          </div>
        </div>

        <div class="chart">
          <div v-for="point in work?.trend ?? []" :key="point.label" class="chart__col">
            <div class="chart__bars">
              <span class="chart__bar is-primary" :style="{ height: `${ratio(point.primary, work?.trend, true)}%` }" :title="`完成 ${point.primary}`" />
              <span class="chart__bar is-secondary" :style="{ height: `${ratio(point.secondary, work?.trend, true)}%` }" :title="`新建 ${point.secondary}`" />
            </div>
            <span class="chart__label">{{ point.label }}</span>
          </div>
        </div>

        <footer class="stats-card__foot">
          <span class="legend is-primary">完成</span>
          <span class="legend is-secondary">新建</span>
          <span class="spacer" />
          <span>进行中项目 {{ work?.activeProjects ?? 0 }} 个</span>
        </footer>
      </section>

      <!-- 学习成长 -->
      <section class="stats-card">
        <header class="stats-card__head">
          <span class="stats-card__icon is-study"><el-icon><Reading /></el-icon></span>
          <div>
            <h2>学习成长</h2>
            <p>每周学习时长与连续记录</p>
          </div>
        </header>

        <div class="kpis">
          <div class="kpi">
            <span>本月时长</span>
            <strong>{{ hours(study?.monthMinutes) }}<em>小时</em></strong>
          </div>
          <div class="kpi">
            <span>连续学习</span>
            <strong>{{ study?.streakDays ?? 0 }}<em>天</em></strong>
          </div>
          <div class="kpi">
            <span>计划完成度</span>
            <strong>{{ (study?.planProgress ?? 0).toFixed(1).replace('.0', '') }}<em>%</em></strong>
          </div>
        </div>

        <div class="chart">
          <div v-for="point in study?.trend ?? []" :key="point.label" class="chart__col">
            <div class="chart__bars">
              <span
                class="chart__bar is-study"
                :style="{ height: `${ratio(point.primary, study?.trend)}%` }"
                :title="`${point.primary} 分钟`"
              />
            </div>
            <span class="chart__label">{{ point.label }}</span>
          </div>
        </div>

        <footer class="stats-card__foot">
          <span class="legend is-study">学习分钟</span>
          <span class="spacer" />
          <span>共 {{ study?.planCount ?? 0 }} 个计划</span>
        </footer>
      </section>

      <!-- 生活收支 -->
      <section class="stats-card">
        <header class="stats-card__head">
          <span class="stats-card__icon is-life"><el-icon><Money /></el-icon></span>
          <div>
            <h2>生活收支</h2>
            <p>近 6 个月支出与收入</p>
          </div>
        </header>

        <div class="kpis">
          <div class="kpi">
            <span>本月支出</span>
            <strong>¥{{ yuan(life?.monthExpense) }}</strong>
          </div>
          <div class="kpi">
            <span>本月收入</span>
            <strong>¥{{ yuan(life?.monthIncome) }}</strong>
          </div>
          <div class="kpi" :class="{ 'is-warn': (life?.balance ?? 0) < 0 }">
            <span>结余</span>
            <strong>¥{{ yuan(life?.balance) }}</strong>
          </div>
        </div>

        <div class="chart chart--life">
          <div v-for="point in life?.trend ?? []" :key="point.label" class="chart__col">
            <div class="chart__bars">
              <span class="chart__bar is-expense" :style="{ height: `${lifeRatio(point.primary)}%` }" :title="`支出 ¥${yuan(point.primary)}`" />
              <span class="chart__bar is-income" :style="{ height: `${lifeRatio(point.secondary)}%` }" :title="`收入 ¥${yuan(point.secondary)}`" />
            </div>
            <span class="chart__label">{{ point.label }}</span>
          </div>
        </div>

        <footer class="stats-card__foot">
          <span class="legend is-expense">支出</span>
          <span class="legend is-income">收入</span>
          <span class="spacer" />
          <span v-if="life?.budgetCents">预算已用 {{ life?.budgetUsedRate ?? 0 }}%</span>
          <span v-else>未设置月预算</span>
        </footer>

        <div v-if="life?.topCategories.length" class="categories">
          <h3>本月支出构成</h3>
          <div v-for="item in life.topCategories" :key="item.name" class="category">
            <span class="category__name">{{ item.name }}</span>
            <span class="category__track">
              <span class="category__fill" :style="{ width: `${Math.max(2, item.ratio)}%` }" />
            </span>
            <span class="category__value">¥{{ yuan(item.cents) }}</span>
          </div>
        </div>
      </section>
    </div>

    <p class="stats-note">
      统计口径：周从周一开始；完成率 = 近 30 天完成 ÷ 同期新建；金额以「分」存储，展示时换算为元。
    </p>
  </section>
</template>

<style scoped lang="scss">
.stats-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;

  h1 {
    font-size: 22px;
    font-weight: 700;
  }
}

.page-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.page-head__range {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.stats-loading {
  @include card($radius-lg);
  padding: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}

.stats-card {
  @include card($radius-lg);
  display: flex;
  flex-direction: column;
  padding: 18px;
  min-height: 352px;
}

.stats-card__head {
  display: flex;
  align-items: center;
  gap: 10px;

  h2 {
    font-size: 14.5px;
    font-weight: 700;
    color: #24393d;
  }

  p {
    margin-top: 2px;
    font-size: 10.5px;
    color: var(--text-tertiary);
  }
}

.stats-card__icon {
  @include flex-center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  font-size: 16px;

  &.is-work {
    background: var(--c-work-bg);
    color: #4a86e8;
  }

  &.is-study {
    background: var(--c-study-bg);
    color: #8b7cf6;
  }

  &.is-life {
    background: var(--c-life-bg);
    color: #ff9f43;
  }
}

.kpis {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
  margin: 14px 0 6px;
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: 2px;

  span {
    font-size: 10px;
    color: var(--text-tertiary);
  }

  strong {
    font-family: var(--font-number);
    font-size: 19px;
    font-weight: 700;
    color: #22383c;

    em {
      margin-left: 2px;
      font-size: 10px;
      font-style: normal;
      font-weight: 500;
      color: var(--text-tertiary);
    }
  }

  &.is-warn strong {
    color: #e25c5c;
  }
}

.chart {
  /* 用固定高度而不是 flex:1：子元素是百分比高度，父级没有确定高度时柱子会塌成 0 */
  display: flex;
  align-items: flex-end;
  gap: 6px;
  height: 150px;
  margin-top: auto;
  padding-top: 12px;
}

.chart__col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  flex: 1;
  height: 100%;
}

.chart__bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  width: 100%;
  height: 100%;
}

.chart__bar {
  width: 7px;
  border-radius: 4px 4px 2px 2px;
  transition: height 600ms $ease-out;

  &.is-primary {
    background: linear-gradient(180deg, #6fe3bd, #35c79b);
  }

  &.is-secondary {
    background: var(--border-color);
  }

  &.is-study {
    width: 12px;
    background: linear-gradient(180deg, #b3a8fb, #8b7cf6);
  }

  &.is-expense {
    background: linear-gradient(180deg, #ffc06a, #ff9f43);
  }

  &.is-income {
    background: var(--border-color);
  }
}

.chart__label {
  font-size: 9px;
  color: var(--text-tertiary);
  white-space: nowrap;
}

.stats-card__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #eef4f1;
  font-size: 10px;
  color: var(--text-tertiary);
}

.legend {
  display: inline-flex;
  align-items: center;
  gap: 4px;

  &::before {
    content: '';
    width: 7px;
    height: 7px;
    border-radius: 2px;
  }

  &.is-primary::before {
    background: #35c79b;
  }

  &.is-secondary::before {
    background: var(--border-color);
  }

  &.is-study::before {
    background: var(--c-study);
  }

  &.is-expense::before {
    background: var(--c-life);
  }

  &.is-income::before {
    background: var(--border-color);
  }
}

.spacer {
  flex: 1;
}

.categories {
  margin-top: 12px;

  h3 {
    margin-bottom: 8px;
    font-size: 11px;
    font-weight: 600;
    color: var(--text-secondary);
  }
}

.category {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 10.5px;
  color: var(--text-secondary);
}

.category__name {
  width: 54px;
  flex: 0 0 54px;
  @include ellipsis(1);
}

.category__track {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;
}

.category__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ffc98d, #ff9f43);
}

.category__value {
  font-family: var(--font-number);
  color: var(--text-tertiary);
}

.stats-note {
  font-size: 10.5px;
  color: var(--text-tertiary);
}

@media (max-width: 1199px) {
  .stats-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .stats-card {
    min-height: auto;
  }

  .chart {
    margin-top: 12px;
    height: 140px;
  }
}
</style>
