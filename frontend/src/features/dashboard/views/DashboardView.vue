<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '../api'
import { taskApi } from '@/features/work/task/api'
import CalendarWidget from '../components/CalendarWidget.vue'
import HeroBanner from '../components/HeroBanner.vue'
import LifeLogWidget from '../components/LifeLogWidget.vue'
import ModuleCard from '../components/ModuleCard.vue'
import QuickEntryBar from '../components/QuickEntryBar.vue'
import StatsOverview from '../components/StatsOverview.vue'
import StudyWidget from '../components/StudyWidget.vue'
import TodoWidget from '../components/TodoWidget.vue'
import { modules, quickEntries, type AppEntry } from '../data'

const router = useRouter()
const queryClient = useQueryClient()

const { data, isLoading, isError, refetch } = useQuery({
  queryKey: ['dashboard', 'summary'],
  queryFn: () => dashboardApi.summary(),
  staleTime: 30_000,
})

const todos = computed(() =>
  (data.value?.todo.items ?? []).map((task, index) => ({
    id: task.id,
    title: task.title,
    time: task.dueTime || '全天',
    done: task.done,
    // 第一条未完成的作为「当前进行项」高亮
    highlight: !task.done && index === 0,
  })),
)

const eventDates = computed(() =>
  (data.value?.calendar.events ?? []).map((item) => item.start.slice(0, 10)),
)

const lifeLogs = computed(() =>
  (data.value?.lifeLogs ?? []).map((log) => ({
    id: log.id,
    title: log.title,
    desc: moodText(log.mood),
    date: formatDate(log.date),
    cover: log.cover,
  })),
)

const toggleMutation = useMutation({
  mutationFn: (payload: { id: string; done: boolean }) =>
    taskApi.toggleDone(payload.id, payload.done),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    queryClient.invalidateQueries({ queryKey: ['tasks'] })
  },
  onError: () => ElMessage.error('操作失败，请稍后重试'),
})

function onToggleTodo(id: string, done: boolean): void {
  toggleMutation.mutate({ id, done })
}

function onQuickEntry(code: string): void {
  const entry = quickEntries.find((item) => item.code === code)
  if (entry) {
    router.push(entry.route)
  }
}

function onOpenApp(app: AppEntry): void {
  router.push(app.route)
}

function onEnterModule(key: string): void {
  const mod = modules.find((item) => item.key === key)
  if (mod) {
    router.push(mod.entry)
  }
}

function onViewAll(): void {
  router.push('/work/todo')
}

function moodText(mood: string | null): string {
  const map: Record<string, string> = {
    happy: '心情不错',
    calm: '平静的一天',
    tired: '有点累',
    sad: '情绪低落',
    excited: '很兴奋',
  }
  return mood ? (map[mood] ?? '记录于生活') : '记录于生活'
}

function formatDate(date: string): string {
  const parts = date.split('-')
  return parts.length === 3 ? `${Number(parts[1])}月${Number(parts[2])}日` : date
}
</script>

<template>
  <div class="dashboard">
    <el-alert
      v-if="isError"
      class="dashboard__alert"
      type="warning"
      :closable="false"
      show-icon
      title="首页数据加载失败"
    >
      <template #default>
        <span>可能是后端未启动。请检查 18080 端口的服务。</span>
        <el-button link type="primary" @click="refetch()">重试</el-button>
      </template>
    </el-alert>

    <HeroBanner :weather="data?.weather ?? null" />

    <QuickEntryBar @open="onQuickEntry" />

    <div class="dashboard__modules">
      <ModuleCard
        v-for="mod in modules"
        :key="mod.key"
        :mod="mod"
        @enter="onEnterModule"
        @open="onOpenApp"
      />
    </div>

    <div class="dashboard__widgets">
      <TodoWidget
        :items="todos"
        :total="data?.todo.total ?? 0"
        :done="data?.todo.done ?? 0"
        :loading="isLoading"
        @toggle="onToggleTodo"
        @add="router.push('/work/todo?new=1')"
        @view-all="onViewAll"
      />
      <StudyWidget
        :total-minutes="data?.study?.totalMinutes ?? 0"
        :target-completion="data?.study?.targetCompletion ?? 0"
        :growth-rate="data?.study?.growthRate ?? null"
        :days="data?.study?.days ?? []"
        @view-all="router.push('/study')"
      />
      <LifeLogWidget
        :items="lifeLogs"
        @view-all="router.push('/life/diary')"
        @add="router.push('/life/diary?new=1')"
      />
      <CalendarWidget :event-dates="eventDates" @view-all="router.push('/calendar')" />
    </div>

    <StatsOverview />
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 0;
}

/* 快速入口压在 Hero 底边上，和设计稿一样形成层次 */
.dashboard > .quick {
  position: relative;
  z-index: 3;
  margin: -46px 26px 0;
}

.dashboard__alert {
  border-radius: $radius-md;
}

.dashboard__modules {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 14px;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.dashboard__widgets {
  display: grid;
  grid-template-columns: 1.16fr 1.2fr 1fr 0.92fr;
  gap: 16px;
  align-items: stretch;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
