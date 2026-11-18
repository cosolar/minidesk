<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  AlarmClock,
  ArrowRight,
  Calendar,
  CircleCheck,
  Clock,
  Delete,
  EditPen,
  Grid,
  List,
  MoreFilled,
  PieChart,
  Plus,
  Search,
  Setting,
  Star,
  Timer,
} from '@element-plus/icons-vue'
import { taskApi, type TaskPayload, type TaskVO } from '../api'
import { projectApi } from '@/features/work/project/api'
import { useClock } from '@/core/composables/useClock'
import { useUserStore } from '@/core/stores/user'
import lakeImage from '@/assets/hero-lake.jpg'

type FilterKey = 'all' | 'today' | 'week' | 'important' | 'done'
type SortKey = 'due' | 'priority' | 'created'
type ViewKey = 'group' | 'flat'
type ModuleKey = '' | 'work' | 'study' | 'life'

interface StatItem {
  key: string
  label: string
  value: number
  unit: string
  tone: string
  icon: Component
  filter: FilterKey
}

interface TaskGroup {
  key: string
  title: string
  tone: string
  items: TaskVO[]
}

const PAGE_SIZE = 100
const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const userStore = useUserStore()
const { now } = useClock(60_000)

const filter = ref<FilterKey>('all')
const sortBy = ref<SortKey>('due')
const viewMode = ref<ViewKey>('group')
const moduleFilter = ref<ModuleKey>('')
const keyword = ref('')

/* ---------------- 数据 ---------------- */

async function fetchAllTasks(): Promise<TaskVO[]> {
  const collected: TaskVO[] = []
  for (let pageNo = 1; pageNo <= 10; pageNo += 1) {
    const res = await taskApi.page({ page: pageNo, size: PAGE_SIZE })
    collected.push(...res.list)
    if (collected.length >= res.total || !res.list.length) {
      break
    }
  }
  return collected
}

const tasksQuery = useQuery({
  queryKey: ['tasks', 'all'],
  queryFn: fetchAllTasks,
  staleTime: 20_000,
})

const tasks = computed(() => tasksQuery.data.value ?? [])
const isLoading = computed(() => tasksQuery.isLoading.value)
const hasError = computed(() => tasksQuery.isError.value)

const todayKey = computed(() => dayjs(now.value).format('YYYY-MM-DD'))
const weekEndKey = computed(() => dayjs(now.value).add(7, 'day').format('YYYY-MM-DD'))

function isPending(task: TaskVO): boolean {
  return !task.done && task.status !== 'cancelled'
}

const pendingTasks = computed(() => tasks.value.filter(isPending))
const doneTasks = computed(() => tasks.value.filter((task) => task.done || task.status === 'done'))
const todayTasks = computed(() => pendingTasks.value.filter((task) => task.dueDate === todayKey.value))
const soonTasks = computed(() =>
  pendingTasks.value.filter(
    (task) => task.dueDate && task.dueDate > todayKey.value && task.dueDate <= weekEndKey.value,
  ),
)
const weekTasks = computed(() =>
  pendingTasks.value.filter((task) => task.dueDate && task.dueDate <= weekEndKey.value),
)
const importantTasks = computed(() => pendingTasks.value.filter((task) => task.priority === 1))

const counts = computed<Record<FilterKey, number>>(() => ({
  all: pendingTasks.value.length,
  today: todayTasks.value.length,
  week: weekTasks.value.length,
  important: importantTasks.value.length,
  done: doneTasks.value.length,
}))

const stats = computed<StatItem[]>(() => [
  {
    key: 'all',
    label: '全部待办',
    value: counts.value.all,
    unit: '条',
    tone: 'var(--brand-500)',
    icon: CircleCheck,
    filter: 'all',
  },
  {
    key: 'today',
    label: '今日待办',
    value: counts.value.today,
    unit: '条',
    tone: 'var(--c-work)',
    icon: PieChart,
    filter: 'today',
  },
  {
    key: 'soon',
    label: '即将到期',
    value: soonTasks.value.length,
    unit: '条',
    tone: 'var(--c-life)',
    icon: AlarmClock,
    filter: 'week',
  },
  {
    key: 'done',
    label: '已完成',
    value: counts.value.done,
    unit: '条',
    tone: 'var(--c-study)',
    icon: Calendar,
    filter: 'done',
  },
])

/* ---------------- 筛选 / 排序 ---------------- */

const filterTabs = computed(() => [
  { key: 'all' as FilterKey, label: '全部', count: counts.value.all },
  { key: 'today' as FilterKey, label: '今天', count: counts.value.today },
  { key: 'week' as FilterKey, label: '最近 7 天', count: counts.value.week },
  { key: 'important' as FilterKey, label: '重要', count: counts.value.important },
  { key: 'done' as FilterKey, label: '已完成', count: counts.value.done },
])

const sortOptions: { key: SortKey; label: string }[] = [
  { key: 'due', label: '按截止时间' },
  { key: 'priority', label: '按优先级' },
  { key: 'created', label: '按创建时间' },
]

const moduleOptions: { key: ModuleKey; label: string }[] = [
  { key: '', label: '全部板块' },
  { key: 'work', label: '工作' },
  { key: 'study', label: '学习' },
  { key: 'life', label: '生活' },
]

const sortLabel = computed(
  () => sortOptions.find((item) => item.key === sortBy.value)?.label ?? '按截止时间',
)
const moduleLabel = computed(
  () => moduleOptions.find((item) => item.key === moduleFilter.value)?.label ?? '全部板块',
)
const filterLabel = computed(
  () => filterTabs.value.find((item) => item.key === filter.value)?.label ?? '全部',
)

function dueScore(task: TaskVO): number {
  if (!task.dueDate) {
    return Number.MAX_SAFE_INTEGER
  }
  const time = task.dueTime && task.dueTime.length >= 5 ? task.dueTime.slice(0, 5) : '23:59'
  return dayjs(`${task.dueDate} ${time}`).valueOf()
}

function sortTasks(list: TaskVO[]): TaskVO[] {
  const cloned = [...list]
  cloned.sort((a, b) => {
    if (sortBy.value === 'priority' && a.priority !== b.priority) {
      return a.priority - b.priority
    }
    if (sortBy.value === 'created') {
      return dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf()
    }
    const gap = dueScore(a) - dueScore(b)
    if (gap !== 0) {
      return gap
    }
    return a.priority - b.priority
  })
  return cloned
}

const filteredTasks = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const matched = (task: TaskVO): boolean => {
    if (moduleFilter.value && task.module !== moduleFilter.value) {
      return false
    }
    if (!kw) {
      return true
    }
    return [task.title, task.note, task.projectName, task.tags]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(kw))
  }

  let base: TaskVO[]
  switch (filter.value) {
    case 'today':
      base = todayTasks.value
      break
    case 'week':
      base = weekTasks.value
      break
    case 'important':
      base = importantTasks.value
      break
    case 'done':
      base = doneTasks.value
      break
    default:
      base = pendingTasks.value
  }
  const list = base.filter(matched)
  if (filter.value === 'done') {
    return [...list].sort((a, b) => {
      const left = a.doneAt ? dayjs(a.doneAt).valueOf() : 0
      const right = b.doneAt ? dayjs(b.doneAt).valueOf() : 0
      return right - left
    })
  }
  return sortTasks(list)
})

const groups = computed<TaskGroup[]>(() => {
  const list = filteredTasks.value
  if (!list.length) {
    return []
  }
  if (filter.value === 'done') {
    return [{ key: 'done', title: '已完成', tone: 'var(--c-study)', items: list }]
  }
  if (viewMode.value === 'flat') {
    return [{ key: 'flat', title: filterLabel.value, tone: 'var(--brand-500)', items: list }]
  }
  if (filter.value === 'today') {
    return [{ key: 'today', title: '今日待办', tone: 'var(--brand-500)', items: list }]
  }
  if (filter.value === 'important') {
    return [{ key: 'important', title: '重要待办', tone: 'var(--c-alert)', items: list }]
  }
  const bucket = (predicate: (task: TaskVO) => boolean): TaskVO[] => list.filter(predicate)
  return [
    {
      key: 'overdue',
      title: '已逾期',
      tone: 'var(--c-alert)',
      items: bucket((task) => !!task.dueDate && task.dueDate < todayKey.value),
    },
    {
      key: 'today',
      title: '今日待办',
      tone: 'var(--brand-500)',
      items: bucket((task) => task.dueDate === todayKey.value),
    },
    {
      key: 'soon',
      title: '即将到期',
      tone: 'var(--c-life)',
      items: bucket(
        (task) => !!task.dueDate && task.dueDate > todayKey.value && task.dueDate <= weekEndKey.value,
      ),
    },
    {
      key: 'later',
      title: '稍后安排',
      tone: 'var(--text-tertiary)',
      items: bucket((task) => !task.dueDate || task.dueDate > weekEndKey.value),
    },
  ].filter((group) => group.items.length > 0)
})

/* ---------------- 展示辅助 ---------------- */

const greeting = computed(() => {
  const hour = dayjs(now.value).hour()
  if (hour < 6) {
    return '凌晨好'
  }
  if (hour < 11) {
    return '上午好'
  }
  if (hour < 14) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})

const dateCard = computed(() => ({
  weekday: WEEKDAYS[dayjs(now.value).day()],
  monthDay: dayjs(now.value).format('MM.DD'),
  year: dayjs(now.value).format('YYYY'),
}))

const remainText = computed(() => {
  const left = counts.value.today
  if (!left) {
    return '今天的事情都清完了，给自己留点空闲吧'
  }
  return `合理规划时间，让每一天都更有意义`
})

function moduleLabelOf(module: string): string {
  if (module === 'study') {
    return '学习'
  }
  if (module === 'life') {
    return '生活'
  }
  return '工作'
}

function isOverdue(task: TaskVO): boolean {
  return !task.done && !!task.dueDate && task.dueDate < todayKey.value
}

function dueLabel(task: TaskVO): string {
  if (task.done && task.doneAt) {
    return `完成于 ${dayjs(task.doneAt).format('M月D日')}`
  }
  if (!task.dueDate) {
    return '无期限'
  }
  const target = dayjs(task.dueDate).startOf('day')
  const diff = target.diff(dayjs(now.value).startOf('day'), 'day')
  const time = task.dueTime ? task.dueTime.slice(0, 5) : ''
  let day: string
  if (diff < -1) {
    day = `逾期 ${-diff} 天`
  } else if (diff === -1) {
    day = '昨天'
  } else if (diff === 0) {
    day = '今天'
  } else if (diff === 1) {
    day = '明天'
  } else if (diff === 2) {
    day = '后天'
  } else if (diff <= 6) {
    day = `周${'日一二三四五六'[target.day()]}`
  } else {
    day = target.format('M月D日')
  }
  return time ? `${day} ${time}` : `${day} · 全天`
}

function rowTitle(task: TaskVO): string | undefined {
  const lines: string[] = []
  if (task.startDate && task.dueDate) {
    lines.push(`工期：${task.startDate} → ${task.dueDate}`)
  }
  if (task.note) {
    lines.push(task.note)
  }
  return lines.length ? lines.join('\n') : undefined
}

/* ---------------- 交互 ---------------- */

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<string | null>(null)
const advanced = ref(false)
const formRef = ref<FormInstance>()

const projectsQuery = useQuery({
  queryKey: ['projects', 'options'],
  queryFn: () => projectApi.list(),
  staleTime: 60_000,
})
const projectOptions = computed(() => projectsQuery.data.value ?? [])

const form = reactive({
  title: '',
  module: 'work' as 'work' | 'study' | 'life',
  priority: 2,
  startDate: '',
  startTime: '',
  dueDate: '',
  dueTime: '',
  projectId: '',
  note: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入要做什么', trigger: 'blur' }],
}

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['tasks'] })
  queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  queryClient.invalidateQueries({ queryKey: ['calendar'] })
}

function resetForm(): void {
  form.title = ''
  form.module = 'work'
  form.priority = 2
  form.startDate = ''
  form.startTime = ''
  form.dueDate = ''
  form.dueTime = ''
  form.projectId = ''
  form.note = ''
  advanced.value = false
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(task: TaskVO): void {
  editingId.value = task.id
  form.title = task.title
  form.module = task.module
  form.priority = task.priority
  form.startDate = task.startDate ?? ''
  form.startTime = task.startTime ?? ''
  form.dueDate = task.dueDate ?? ''
  form.dueTime = task.dueTime ?? ''
  form.projectId = task.projectId ?? ''
  form.note = task.note ?? ''
  advanced.value = Boolean(task.startDate || task.startTime || task.note)
  dialogVisible.value = true
}

const saveMutation = useMutation({
  mutationFn: (payload: { id: string | null; body: TaskPayload }) =>
    payload.id ? taskApi.update(payload.id, payload.body) : taskApi.create(payload.body),
  onSuccess: (_data, variables) => {
    ElMessage.success(variables.id ? '已保存' : '已添加')
    dialogVisible.value = false
    resetForm()
    invalidate()
  },
  onError: () => ElMessage.error('保存失败，请稍后重试'),
})

const toggleMutation = useMutation({
  mutationFn: (payload: { id: string; done: boolean }) => taskApi.toggleDone(payload.id, payload.done),
  onSuccess: invalidate,
  onError: () => ElMessage.error('操作失败'),
})

const patchMutation = useMutation({
  mutationFn: (payload: { task: TaskVO; body: TaskPayload }) =>
    taskApi.update(payload.task.id, payload.body),
  onSuccess: invalidate,
  onError: () => ElMessage.error('操作失败'),
})

const removeMutation = useMutation({
  mutationFn: (id: string) => taskApi.remove(id),
  onSuccess: () => {
    ElMessage.success('已删除')
    invalidate()
  },
  onError: () => ElMessage.error('删除失败'),
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await saveMutation.mutateAsync({
      id: editingId.value,
      body: {
        title: form.title.trim(),
        module: form.module,
        priority: form.priority,
        startDate: form.startDate || null,
        startTime: form.startTime || null,
        dueDate: form.dueDate || null,
        dueTime: form.dueTime || null,
        projectId: form.projectId || null,
        note: form.note || null,
      },
    })
  } catch {
    /* 错误提示已由 mutation 处理 */
  } finally {
    submitting.value = false
  }
}

function toggleDone(task: TaskVO): void {
  toggleMutation.mutate({ id: task.id, done: !task.done })
}

function toggleImportant(task: TaskVO): void {
  patchMutation.mutate({
    task,
    body: {
      title: task.title,
      note: task.note,
      module: task.module,
      priority: task.priority === 1 ? 2 : 1,
      startDate: task.startDate,
      startTime: task.startTime,
      dueDate: task.dueDate,
      dueTime: task.dueTime,
      projectId: task.projectId,
    },
  })
}

function moveToToday(task: TaskVO): void {
  patchMutation.mutate({
    task,
    body: {
      title: task.title,
      note: task.note,
      module: task.module,
      priority: task.priority,
      startDate: task.startDate,
      startTime: task.startTime,
      dueDate: todayKey.value,
      dueTime: task.dueTime,
      projectId: task.projectId,
    },
  })
  ElMessage.success('已排到今天的清单')
}

async function remove(task: TaskVO): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${task.title}」？`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    removeMutation.mutate(task.id)
  }
}

function setFilter(key: FilterKey): void {
  filter.value = key
  if (key === 'done') {
    sortBy.value = 'created'
  }
}

function retry(): void {
  void tasksQuery.refetch()
}

const quickActions = [
  { key: 'add', label: '添加待办', tone: 'var(--brand-500)', icon: Plus },
  { key: 'calendar', label: '查看日历', tone: 'var(--c-work)', icon: Calendar },
  { key: 'project', label: '项目分组', tone: 'var(--c-study)', icon: Grid },
  { key: 'setting', label: '偏好设置', tone: 'var(--c-life)', icon: Setting },
]

function onQuickAction(key: string): void {
  if (key === 'add') {
    openCreate()
    return
  }
  if (key === 'calendar') {
    router.push('/calendar')
    return
  }
  if (key === 'project') {
    router.push('/work/project')
    return
  }
  router.push('/settings')
}

onMounted(() => {
  const moduleParam = route.query.module
  if (moduleParam === 'work' || moduleParam === 'study' || moduleParam === 'life') {
    moduleFilter.value = moduleParam
  }
  if (route.query.new) {
    openCreate()
    router.replace({ query: moduleFilter.value ? { module: moduleFilter.value } : {} })
  }
})
</script>

<template>
  <section class="td-page">
    <div class="td-scene" aria-hidden="true">
      <img class="td-scene__photo" :src="lakeImage" alt="" />
      <span class="td-scene__veil" />
      <span class="td-scene__fade" />
    </div>

    <header class="td-hero">
      <div class="td-hero__text">
        <h1>{{ greeting }}，{{ userStore.displayName }} <span class="td-hero__wave">👋</span></h1>
        <p>
          {{ remainText }}
          <span class="td-hero__date">· {{ dateCard.weekday }} {{ dateCard.monthDay }}</span>
        </p>
      </div>

      <div class="td-datecard" aria-hidden="true">
        <span class="td-datecard__week">{{ dateCard.weekday }}</span>
        <strong class="td-datecard__day">{{ dateCard.monthDay }}</strong>
        <span class="td-datecard__year">{{ dateCard.year }}</span>
        <span class="td-datecard__note">加油！</span>
      </div>
    </header>

    <ul class="td-stats">
      <li v-for="item in stats" :key="item.key">
        <button
          type="button"
          class="td-stat"
          :class="{ 'is-active': filter === item.filter }"
          :style="{ '--tone': item.tone }"
          @click="setFilter(item.filter)"
        >
          <span class="td-stat__icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="td-stat__text">
            <span class="td-stat__label">{{ item.label }}</span>
            <span class="td-stat__value">
              {{ item.value }}<small>{{ item.unit }}</small>
            </span>
          </span>
          <el-icon class="td-stat__go"><ArrowRight /></el-icon>
        </button>
      </li>
    </ul>

    <div class="td-body">
      <div class="td-main">
        <div class="td-toolbar">
          <div class="td-pills">
            <button
              v-for="tab in filterTabs"
              :key="tab.key"
              type="button"
              class="td-pill"
              :class="{ 'is-active': filter === tab.key }"
              @click="setFilter(tab.key)"
            >
              {{ tab.label }}
              <span class="td-pill__num">{{ tab.count }}</span>
            </button>
          </div>

          <div class="td-tools">
            <el-dropdown trigger="click" placement="bottom-end">
              <button type="button" class="td-tool">
                <el-icon><Timer /></el-icon>{{ sortLabel }}
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in sortOptions"
                    :key="item.key"
                    :class="{ 'is-current': sortBy === item.key }"
                    @click="sortBy = item.key"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-dropdown trigger="click" placement="bottom-end">
              <button type="button" class="td-tool td-tool--module" :class="{ 'is-on': moduleFilter }">
                <el-icon><Grid /></el-icon>{{ moduleLabel }}
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in moduleOptions"
                    :key="item.key || 'all'"
                    :class="{ 'is-current': moduleFilter === item.key }"
                    @click="moduleFilter = item.key"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <label class="td-search">
              <el-icon><Search /></el-icon>
              <input v-model="keyword" type="search" placeholder="搜索待办" aria-label="搜索待办" />
            </label>

            <div class="td-views" role="group" aria-label="展示方式">
              <button
                type="button"
                :class="{ 'is-active': viewMode === 'group' }"
                title="分组查看"
                @click="viewMode = 'group'"
              >
                <el-icon><Grid /></el-icon>
              </button>
              <button
                type="button"
                :class="{ 'is-active': viewMode === 'flat' }"
                title="平铺查看"
                @click="viewMode = 'flat'"
              >
                <el-icon><List /></el-icon>
              </button>
            </div>
          </div>
        </div>

        <div v-if="hasError" class="td-panel td-panel--error">
          <p>待办没能加载出来，可能是后端服务没有启动。</p>
          <button type="button" class="td-ghost" @click="retry">重新加载</button>
        </div>

        <div v-else-if="isLoading" class="td-panel">
          <div class="td-skeleton">
            <span v-for="index in 4" :key="index" class="td-skeleton__row" />
          </div>
        </div>

        <template v-else-if="groups.length">
          <section
            v-for="group in groups"
            :key="group.key"
            class="td-panel td-group"
            :style="{ '--tone': group.tone }"
          >
            <header class="td-group__head">
              <span class="td-group__bar" aria-hidden="true" />
              <h2>{{ group.title }}</h2>
              <span class="td-group__count">共 {{ group.items.length }} 项</span>
            </header>

            <ul class="td-tasks">
              <li
                v-for="task in group.items"
                :key="task.id"
                class="td-task"
                :class="{ 'is-done': task.done, 'is-overdue': isOverdue(task) }"
                :title="rowTitle(task)"
              >
                <button
                  type="button"
                  class="td-check"
                  :aria-pressed="task.done"
                  :aria-label="task.done ? '标记为未完成' : '标记为已完成'"
                  @click="toggleDone(task)"
                >
                  <svg v-if="task.done" viewBox="0 0 16 16" width="11" height="11" aria-hidden="true">
                    <path
                      d="M3.5 8.4 6.4 11 12.5 4.9"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.9"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>

                <div class="td-task__main">
                  <div class="td-task__title">
                    <span class="td-task__text">{{ task.title }}</span>
                    <span v-if="task.priority === 1 && !task.done" class="td-flag">重要</span>
                    <span class="td-task__module" :class="`is-${task.module}`">
                      {{ moduleLabelOf(task.module) }}
                    </span>
                  </div>
                  <div
                    v-if="task.projectName || task.note"
                    class="td-task__sub"
                  >
                    <span v-if="task.projectName" class="td-task__project">
                      {{ task.projectName }}
                    </span>
                    <span v-if="task.note" class="td-task__note">{{ task.note }}</span>
                  </div>
                </div>

                <span class="td-task__due">
                  <el-icon><Clock /></el-icon>
                  <span>{{ dueLabel(task) }}</span>
                </span>

                <el-dropdown trigger="click" placement="bottom-end">
                  <button type="button" class="td-task__more" aria-label="更多操作">
                    <el-icon><MoreFilled /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openEdit(task)">
                        <el-icon><EditPen /></el-icon>编辑待办
                      </el-dropdown-item>
                      <el-dropdown-item @click="toggleImportant(task)">
                        <el-icon><Star /></el-icon>
                        {{ task.priority === 1 ? '取消重要' : '标记为重要' }}
                      </el-dropdown-item>
                      <el-dropdown-item
                        v-if="!task.done && task.dueDate !== todayKey"
                        @click="moveToToday(task)"
                      >
                        <el-icon><AlarmClock /></el-icon>排到今天
                      </el-dropdown-item>
                      <el-dropdown-item divided @click="remove(task)">
                        <el-icon><Delete /></el-icon>删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </li>
            </ul>
          </section>
        </template>

        <div v-else class="td-panel td-empty">
          <span class="td-empty__icon">
            <el-icon><CircleCheck /></el-icon>
          </span>
          <h2>{{ keyword || moduleFilter ? '没有匹配的待办' : '这里还空着' }}</h2>
          <p>
            {{
              keyword || moduleFilter
                ? '换个关键词或板块再试试。'
                : '把要做的、想做的都记下来，一条一条清掉。'
            }}
          </p>
          <button type="button" class="td-primary" @click="openCreate">
            <el-icon><Plus /></el-icon>添加第一条待办
          </button>
        </div>
      </div>

      <aside class="td-side">
        <section class="td-quick">
          <header class="td-quick__head">
            <h2>快捷操作</h2>
          </header>
          <div class="td-actions">
            <button
              v-for="action in quickActions"
              :key="action.key"
              type="button"
              class="td-action"
              :style="{ '--tone': action.tone }"
              @click="onQuickAction(action.key)"
            >
              <span class="td-action__icon">
                <el-icon><component :is="action.icon" /></el-icon>
              </span>
              <span class="td-action__label">{{ action.label }}</span>
            </button>
          </div>
        </section>

        <section class="td-quote">
          <span class="td-quote__leaf" aria-hidden="true">🍃</span>
          <p>坚持做更好的自己</p>
          <p>每天进步一点点</p>
        </section>
      </aside>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑待办' : '添加待办'"
      width="480px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="要做什么" prop="title">
          <el-input v-model="form.title" placeholder="例如：整理本周项目复盘" maxlength="200" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="板块">
            <el-select v-model="form.module" style="width: 100%">
              <el-option label="工作" value="work" />
              <el-option label="学习" value="study" />
              <el-option label="生活" value="life" />
            </el-select>
          </el-form-item>

          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="高（重要）" :value="1" />
              <el-option label="中" :value="2" />
              <el-option label="低" :value="3" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="截止日期">
            <el-date-picker
              v-model="form.dueDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="截止时间">
            <el-time-select
              v-model="form.dueTime"
              start="06:00"
              step="00:30"
              end="23:30"
              placeholder="全天"
              clearable
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="所属项目">
          <el-select v-model="form.projectId" placeholder="不挂项目" clearable style="width: 100%">
            <el-option
              v-for="item in projectOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <button
          type="button"
          class="form-more"
          :class="{ 'is-open': advanced }"
          @click="advanced = !advanced"
        >
          <el-icon><ArrowRight /></el-icon>
          更多设置（开始时间、备注）
        </button>

        <div v-if="advanced" class="form-advanced">
          <div class="form-row">
            <el-form-item label="开始日期">
              <el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="工期起点"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="开始时间">
              <el-time-select
                v-model="form.startTime"
                start="06:00"
                step="00:30"
                end="23:30"
                placeholder="可选"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </div>

          <el-form-item label="备注">
            <el-input v-model="form.note" type="textarea" :rows="3" placeholder="补充说明（可选）" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <button type="button" class="td-ghost" @click="dialogVisible = false">取消</button>
        <button type="button" class="td-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : editingId ? '保存' : '添加' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.td-page {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 100%;
  margin-top: -4px;
}

/* ---------- 背景山水 ---------- */
.td-scene {
  position: absolute;
  top: -4px;
  left: calc(-1 * $content-padding);
  right: calc(-1 * $content-padding);
  height: 460px;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.td-scene__photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 56% 40%;
  filter: var(--hero-photo-filter);
  opacity: var(--hero-photo-opacity);
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 46%, rgba(0, 0, 0, 0.5) 74%, transparent 100%);
  mask-image: linear-gradient(180deg, #000 0%, #000 46%, rgba(0, 0, 0, 0.5) 74%, transparent 100%);
}

.td-scene__veil {
  position: absolute;
  inset: 0;
  background:
    var(--hero-haze),
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--bg-page) 82%, transparent) 0%,
      color-mix(in srgb, var(--bg-page) 46%, transparent) 32%,
      transparent 62%
    );
}

.td-scene__fade {
  position: absolute;
  inset: auto 0 0 0;
  height: 150px;
  background: linear-gradient(180deg, transparent 0%, var(--hero-fade-mid) 58%, var(--bg-page) 100%);
}

/* ---------- 头部 ---------- */
.td-hero {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 14px 18px;

  h1 {
    font-family: var(--font-display);
    font-size: 26px;
    font-weight: 700;
    line-height: 1.25;
    letter-spacing: 0.4px;
    color: var(--hero-title);
  }

  p {
    margin-top: 7px;
    font-size: 12.5px;
    letter-spacing: 0.3px;
    color: var(--hero-sub);
  }
}

.td-hero__date {
  display: none;
}

.td-hero__wave {
  display: inline-block;
  margin-left: 2px;
  font-size: 20px;
  transform: rotate(-8deg);
}

.td-datecard {
  position: relative;
  flex: 0 0 auto;
  width: 138px;
  padding: 16px 14px 18px;
  border-radius: 16px;
  text-align: center;
  background: linear-gradient(180deg, #ffffff 0%, #fbfefd 68%, #f3fbf8 100%);
  box-shadow: 0 16px 32px rgba(23, 74, 60, 0.16), inset 0 0 0 1px rgba(255, 255, 255, 0.9);
  transform: rotate(-2.6deg);
  transition: transform $duration-base $ease-out;

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: -5px;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: color-mix(in srgb, var(--brand-500) 55%, #ffffff);
    box-shadow: 0 1px 3px rgba(23, 74, 60, 0.25);
  }

  &::before {
    left: 28px;
  }

  &::after {
    right: 28px;
  }

  &:hover {
    transform: rotate(-1deg) translateY(-2px);
  }
}

.td-datecard__week {
  display: block;
  font-size: 11px;
  letter-spacing: 2px;
  color: var(--text-secondary);
}

.td-datecard__day {
  display: block;
  margin: 4px 0 2px;
  font-family: var(--font-number);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 1px;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.td-datecard__year {
  display: block;
  font-family: var(--font-number);
  font-size: 11.5px;
  letter-spacing: 1.4px;
  color: var(--text-tertiary);
}

.td-datecard__note {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--brand-500);
  transform: rotate(-5deg);
}

/* ---------- 数据概览 ---------- */
.td-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 0 14px;
  margin: 2px 0 0;
  list-style: none;
}

.td-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  background: var(--surface-glass-strong);
  box-shadow: 0 10px 26px rgba(23, 74, 60, 0.07);
  backdrop-filter: blur(16px) saturate(1.1);
  -webkit-backdrop-filter: blur(16px) saturate(1.1);
  cursor: pointer;
  transition:
    transform $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 16px 32px rgba(23, 74, 60, 0.12);
    border-color: color-mix(in srgb, var(--tone) 28%, var(--glass-border));
  }

  &.is-active {
    border-color: color-mix(in srgb, var(--tone) 42%, transparent);
    box-shadow: 0 14px 30px color-mix(in srgb, var(--tone) 16%, transparent);
  }

  &.is-active .td-stat__label {
    color: color-mix(in srgb, var(--tone) 78%, var(--text-secondary));
  }
}

.td-stat__icon {
  @include flex-center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 19px;
  color: var(--tone);
  background: color-mix(in srgb, var(--tone) 13%, var(--bg-card));
}

.td-stat__text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.td-stat__label {
  font-size: 11.5px;
  letter-spacing: 0.3px;
  color: var(--text-secondary);
}

.td-stat__value {
  font-family: var(--font-number);
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;

  small {
    margin-left: 2px;
    font-family: var(--font-sans);
    font-size: 10.5px;
    font-weight: 400;
    color: var(--text-tertiary);
  }
}

.td-stat__go {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-tertiary);
  opacity: 0.55;
}

/* ---------- 主体两栏 ---------- */
.td-body {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 232px;
  gap: 16px;
  align-items: start;
  padding: 22px 14px 24px;

  @include respond-below(lg) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.td-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

/* ---------- 工具栏 ---------- */
.td-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.td-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.td-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px 0 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--surface-glass);
  color: var(--text-secondary);
  font-size: 12.5px;
  white-space: nowrap;
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition:
    color $duration-fast $ease-out,
    background $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
  }

  &.is-active {
    background: linear-gradient(
      135deg,
      var(--brand-500) 0%,
      color-mix(in srgb, var(--brand-500) 72%, var(--brand-400)) 100%
    );
    color: var(--text-inverse);
    font-weight: 600;
    box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 30%, transparent);
  }
}

.td-pill__num {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--border-strong) 55%, transparent);
  color: var(--text-secondary);
  font-size: 10.5px;
  line-height: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  transition: background $duration-fast $ease-out;

  .td-pill.is-active & {
    background: rgba(255, 255, 255, 0.24);
    color: #fff;
  }
}

.td-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-left: auto;
}

.td-tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, var(--border-color) 80%, var(--bg-card));
  border-radius: 999px;
  background: var(--surface-glass-strong);
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    border-color: var(--brand-200);
  }

  &.is-on {
    color: var(--brand-600);
    border-color: var(--brand-200);
    background: var(--brand-50);
  }

  .el-icon {
    font-size: 13px;
  }
}

.td-search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  width: 152px;
  border: 1px solid color-mix(in srgb, var(--border-color) 80%, var(--bg-card));
  border-radius: 999px;
  background: var(--surface-glass-strong);
  color: var(--text-tertiary);
  transition: border-color $duration-fast $ease-out;

  &:focus-within {
    border-color: var(--brand-200);
  }

  input {
    flex: 1;
    min-width: 0;
    border: none;
    outline: none;
    background: transparent;
    color: var(--text-primary);
    font-size: 12px;

    &::placeholder {
      color: var(--text-tertiary);
    }

    &::-webkit-search-cancel-button {
      display: none;
    }
  }
}

.td-views {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: 999px;
  background: var(--surface-glass-strong);
  border: 1px solid color-mix(in srgb, var(--border-color) 80%, var(--bg-card));

  button {
    @include flex-center;
    width: 28px;
    height: 26px;
    border: none;
    border-radius: 999px;
    background: transparent;
    color: var(--text-tertiary);
    font-size: 13px;
    cursor: pointer;
    transition: all $duration-fast $ease-out;

    &.is-active {
      background: var(--brand-500);
      color: #fff;
      box-shadow: 0 4px 10px color-mix(in srgb, var(--brand-500) 32%, transparent);
    }
  }
}

/* ---------- 分组面板 ---------- */
.td-panel {
  @include card(18px);
  padding: 8px 12px 10px;
}

.td-panel--error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 22px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.td-group {
  --tone: var(--brand-500);
  animation: td-rise 0.28s $ease-out both;
}

.td-group__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 8px 8px;

  h2 {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }
}

.td-group__bar {
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--tone);
}

.td-group__count {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

.td-tasks {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.td-task {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 8px;
  border-radius: 12px;
  transition: background $duration-fast $ease-out;

  & + & {
    border-top: 1px solid color-mix(in srgb, var(--border-color) 62%, transparent);
  }

  &:hover {
    background: var(--bg-hover);

    .td-task__more {
      opacity: 1;
    }
  }

  &.is-done {
    .td-task__text {
      color: var(--text-tertiary);
      text-decoration: line-through;
      text-decoration-thickness: 1px;
    }
  }

  &.is-overdue .td-task__due {
    color: var(--c-alert);
  }
}

.td-check {
  @include flex-center;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 1.5px solid var(--border-strong);
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
  transition:
    border-color $duration-fast $ease-out,
    background $duration-fast $ease-out;

  &:hover {
    border-color: var(--brand-400);
  }

  .is-done & {
    border-color: var(--brand-500);
    background: var(--brand-500);
  }
}

.td-task__main {
  flex: 1;
  min-width: 0;
}

.td-task__title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.td-task__text {
  @include ellipsis(1);
  font-size: 13.5px;
  color: var(--text-primary);
}

.td-flag {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--c-alert-bg);
  color: var(--c-alert);
  font-size: 10px;
  line-height: 15px;
}

.td-task__sub {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.td-task__project {
  padding: 0 6px;
  border-radius: 4px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 10.5px;
  line-height: 16px;
}

.td-task__note {
  @include ellipsis(1);
  max-width: 300px;
}

.td-task__module {
  flex: 0 0 auto;
  margin-left: 2px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 10.5px;
  line-height: 16px;

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

.td-task__due {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  min-width: 96px;
  justify-content: flex-end;
  font-size: 11.5px;
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;

  .el-icon {
    font-size: 12px;
  }
}

.td-task__more {
  @include flex-center;
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  opacity: 0.55;
  transition:
    opacity $duration-fast $ease-out,
    background $duration-fast $ease-out;

  &:hover {
    background: var(--bg-subtle);
    color: var(--brand-600);
  }

  @media (hover: none) {
    opacity: 1;
  }
}

/* ---------- 空 / 加载 ---------- */
.td-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 52px 20px 56px;
  text-align: center;

  h2 {
    margin-top: 4px;
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary);
  }

  p {
    font-size: 12px;
    color: var(--text-tertiary);
  }

  .td-primary {
    margin-top: 8px;
  }
}

.td-empty__icon {
  @include flex-center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  font-size: 24px;
  color: var(--brand-500);
  background: var(--brand-50);
}

.td-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 6px;
}

.td-skeleton__row {
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(
    90deg,
    var(--bg-subtle) 0%,
    color-mix(in srgb, var(--bg-subtle) 55%, var(--bg-card)) 50%,
    var(--bg-subtle) 100%
  );
  background-size: 220% 100%;
  animation: td-shimmer 1.4s linear infinite;
}

/* ---------- 右栏 ---------- */
.td-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: sticky;
  top: 4px;

  @include respond-below(lg) {
    position: static;
  }
}

.td-quick {
  @include card(18px);
  padding: 14px 14px 16px;
}

.td-quick__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;

  h2 {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
  }
}

.td-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.td-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 6px 12px;
  border: 1px solid color-mix(in srgb, var(--tone) 16%, transparent);
  border-radius: 14px;
  background: color-mix(in srgb, var(--tone) 7%, var(--bg-card));
  cursor: pointer;
  transition:
    transform $duration-fast $ease-out,
    box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 20px color-mix(in srgb, var(--tone) 16%, transparent);
  }
}

.td-action__icon {
  @include flex-center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--tone) 72%, #ffffff) 0%,
    var(--tone) 100%
  );
  box-shadow: 0 6px 14px color-mix(in srgb, var(--tone) 26%, transparent);
}

.td-action__label {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.td-quote {
  @include card(18px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 18px 14px 20px;
  text-align: center;
  font-size: 11.5px;
  line-height: 1.8;
  color: var(--text-secondary);
  background:
    radial-gradient(
      120% 80% at 50% 120%,
      color-mix(in srgb, var(--brand-400) 12%, transparent) 0%,
      transparent 70%
    ),
    var(--surface-glass);
}

.td-quote__leaf {
  font-size: 16px;
  line-height: 1;
}

/* ---------- 通用按钮 ---------- */
.td-primary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: var(--brand-500);
  color: #fff;
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 26%, transparent);
  transition:
    background $duration-fast $ease-out,
    transform $duration-fast $ease-out;

  &:hover {
    background: var(--brand-600);
  }

  &:active {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.td-ghost {
  height: 34px;
  padding: 0 16px;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition:
    color $duration-fast $ease-out,
    border-color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    border-color: var(--brand-200);
  }
}

/* ---------- 表单 ---------- */
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-more {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 2px 0 12px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 12px;
  cursor: pointer;
  transition: color $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
  }

  .el-icon {
    font-size: 12px;
    transition: transform $duration-fast $ease-out;
  }

  &.is-open .el-icon {
    transform: rotate(90deg);
  }
}

.form-advanced {
  padding-top: 4px;
}

:deep(.el-dropdown-menu__item.is-current) {
  color: var(--brand-600);
  font-weight: 600;
}

/* ---------- 动效 ---------- */
@keyframes td-rise {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes td-shimmer {
  from {
    background-position: 120% 0;
  }
  to {
    background-position: -120% 0;
  }
}

/* ---------- 窄屏 ---------- */
@include respond-below(sm) {
  .td-scene {
    height: 340px;
  }

  .td-hero {
    padding: 16px 2px 12px;
    gap: 12px;

    h1 {
      font-size: 21px;
    }
  }

  .td-datecard {
    display: none;
  }

  .td-hero__date {
    display: inline;
  }

  .td-stats {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    padding: 0;
  }

  .td-stat {
    padding: 10px 12px;
    gap: 9px;
    border-radius: 15px;

    &__icon {
      width: 32px;
      height: 32px;
      font-size: 16px;
    }

    &__value {
      font-size: 19px;
    }
  }

  .td-body {
    padding: 12px 0 16px;
  }

  .td-stats {
    padding: 0;
  }

  .td-pills {
    flex-wrap: nowrap;
    overflow-x: auto;
    max-width: 100%;
    padding-bottom: 2px;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .td-tool--module {
    display: none;
  }

  .td-tools {
    width: 100%;
    margin-left: 0;
    flex-wrap: nowrap;
  }

  .td-search {
    flex: 1;
    width: auto;
    min-width: 120px;
  }

  .td-task__module {
    display: none;
  }

  .td-task__due {
    min-width: 0;
  }

  .td-task__text {
    display: -webkit-box;
    white-space: normal;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .td-task__text {
    display: -webkit-box;
    white-space: normal;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
}
</style>
