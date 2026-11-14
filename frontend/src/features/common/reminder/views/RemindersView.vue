<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { ArrowRight, Bell, RefreshRight } from '@element-plus/icons-vue'
import { reminderApi, type ReminderKind, type ReminderVO } from '../api'
import { taskApi } from '@/features/work/task/api'
import { useUserStore } from '@/core/stores/user'

const router = useRouter()
const queryClient = useQueryClient()
const userStore = useUserStore()

const KIND_LABEL: Record<ReminderKind, string> = {
  task: '任务',
  birthday: '生日',
  anniversary: '纪念日',
  countdown: '倒数日',
  plan: '计划',
}

const FILTERS: Array<{ key: string; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'task', label: '任务' },
  { key: 'birthday', label: '生日' },
  { key: 'anniversary', label: '纪念日' },
  { key: 'plan', label: '计划' },
]

const activeFilter = ref('all')

const listQuery = useQuery({
  queryKey: ['reminders'],
  queryFn: () => reminderApi.list(),
  staleTime: 30_000,
})

const items = computed<ReminderVO[]>(() => listQuery.data.value ?? [])
const filtered = computed(() =>
  activeFilter.value === 'all' ? items.value : items.value.filter((item) => item.kind === activeFilter.value),
)

const stats = computed(() => ({
  overdue: items.value.filter((item) => item.level === 'overdue').length,
  today: items.value.filter((item) => item.level === 'today').length,
  urgent: items.value.filter((item) => item.level === 'urgent').length,
}))

const doneMutation = useMutation({
  mutationFn: (taskId: string) => taskApi.toggleDone(taskId, true),
  onSuccess: () => {
    ElMessage.success('已标记完成')
    queryClient.invalidateQueries({ queryKey: ['reminders'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  },
})

function open(item: ReminderVO): void {
  router.push(item.route)
}

function complete(item: ReminderVO): void {
  if (item.kind !== 'task') {
    return
  }
  doneMutation.mutate(item.id.replace('task-', ''))
}

function dayText(days: number): string {
  if (days < 0) {
    return `逾期 ${Math.abs(days)} 天`
  }
  if (days === 0) {
    return '今天'
  }
  if (days === 1) {
    return '明天'
  }
  return `${days} 天后`
}

function levelClass(item: ReminderVO): string {
  return `is-${item.level}`
}

const greeting = computed(() => (stats.value.overdue > 0 ? '先处理逾期的事项' : '节奏不错，继续保持'))
</script>

<template>
  <section class="reminder-page">
    <header class="page-head">
      <div>
        <h1>提醒中心</h1>
        <p class="page-head__sub">
          {{ userStore.displayName }}，{{ greeting }}
        </p>
      </div>
      <el-button :icon="RefreshRight" :loading="listQuery.isFetching.value" @click="listQuery.refetch()">
        刷新
      </el-button>
    </header>

    <div class="reminder-stats">
      <div class="stat-card is-overdue">
        <span class="stat-card__label">已逾期</span>
        <strong>{{ stats.overdue }}</strong>
      </div>
      <div class="stat-card is-today">
        <span class="stat-card__label">今天到期</span>
        <strong>{{ stats.today }}</strong>
      </div>
      <div class="stat-card is-urgent">
        <span class="stat-card__label">三天内</span>
        <strong>{{ stats.urgent }}</strong>
      </div>
      <div class="stat-card">
        <span class="stat-card__label">全部提醒</span>
        <strong>{{ items.length }}</strong>
      </div>
    </div>

    <div class="reminder-filters">
      <button
        v-for="filter in FILTERS"
        :key="filter.key"
        type="button"
        class="reminder-filters__item"
        :class="{ 'is-active': activeFilter === filter.key }"
        @click="activeFilter = filter.key"
      >
        {{ filter.label }}
      </button>
    </div>

    <div class="reminder-list">
      <el-skeleton v-if="listQuery.isLoading.value" :rows="6" animated />

      <div v-else-if="!filtered.length" class="reminder-empty">
        <el-icon class="reminder-empty__icon"><Bell /></el-icon>
        <p>没有待处理的提醒</p>
        <span>任务截止、生日、纪念日与计划到期都会出现在这里</span>
      </div>

      <template v-else>
        <article
          v-for="item in filtered"
          :key="item.id"
          class="reminder-item"
          :class="levelClass(item)"
          @click="open(item)"
        >
        <div class="reminder-item__date">
          <span class="reminder-item__day">{{ item.date.slice(8, 10) }}</span>
          <span class="reminder-item__month">{{ item.date.slice(5, 7) }}月</span>
        </div>

        <div class="reminder-item__body">
          <div class="reminder-item__title">{{ item.title }}</div>
          <div class="reminder-item__meta">
            <span class="reminder-item__tag">{{ KIND_LABEL[item.kind] }}</span>
            <span>{{ item.subtitle }}</span>
            <span>{{ item.date }}</span>
          </div>
        </div>

        <span class="reminder-item__days">{{ dayText(item.days) }}</span>

          <div class="reminder-item__ops" @click.stop>
            <el-button
              v-if="item.kind === 'task'"
              size="small"
              text
              type="success"
              @click="complete(item)"
            >
              完成
            </el-button>
            <el-icon class="reminder-item__go"><ArrowRight /></el-icon>
          </div>
        </article>
      </template>
    </div>
  </section>
</template>

<style scoped lang="scss">
.reminder-page {
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

.reminder-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  @include card($radius-lg);
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;

  strong {
    font-family: var(--font-number);
    font-size: 24px;
    font-weight: 700;
    color: #22383c;
  }

  &.is-overdue strong {
    color: #e25c5c;
  }

  &.is-today strong {
    color: #f08a2a;
  }

  &.is-urgent strong {
    color: #d8a11c;
  }
}

.stat-card__label {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.reminder-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.reminder-filters__item {
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all $duration-fast $ease-out;

  &.is-active {
    border-color: var(--brand-200);
    background: var(--brand-50);
    color: var(--brand-700);
    font-weight: 600;
  }
}

.reminder-list {
  @include card($radius-lg);
  padding: 10px 14px;
}

.reminder-empty {
  @include flex-center;
  flex-direction: column;
  gap: 6px;
  padding: 60px 0;
  color: var(--text-tertiary);

  p {
    font-size: 14px;
    color: var(--text-secondary);
  }

  span {
    font-size: 11.5px;
  }
}

.reminder-empty__icon {
  font-size: 28px;
  color: var(--brand-200);
}

.reminder-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-radius: 12px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background $duration-fast $ease-out;

  & + & {
    border-top: 1px solid #f4f8f6;
  }

  &:hover {
    background: var(--bg-subtle);
  }

  &.is-overdue {
    border-left-color: #e25c5c;
  }

  &.is-today {
    border-left-color: #f08a2a;
  }

  &.is-urgent {
    border-left-color: #f0c040;
  }

  &.is-soon {
    border-left-color: var(--brand-200);
  }
}

.reminder-item__date {
  @include flex-center;
  flex-direction: column;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  border-radius: 12px;
  background: var(--bg-subtle);
  color: #4a5f63;
}

.reminder-item__day {
  font-family: var(--font-number);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.1;
}

.reminder-item__month {
  font-size: 10px;
  color: var(--text-tertiary);
}

.reminder-item__body {
  flex: 1;
  min-width: 0;
}

.reminder-item__title {
  font-size: 13.5px;
  font-weight: 600;
  color: #24393d;
  @include ellipsis(1);
}

.reminder-item__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.reminder-item__tag {
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--bg-subtle);
  color: #6b8079;
}

.reminder-item__days {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;

  .is-overdue & {
    color: #e25c5c;
  }

  .is-today & {
    color: #f08a2a;
  }
}

.reminder-item__ops {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-tertiary);
}

@media (max-width: 767px) {
  .reminder-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .reminder-item {
    flex-wrap: wrap;
  }
}
</style>
